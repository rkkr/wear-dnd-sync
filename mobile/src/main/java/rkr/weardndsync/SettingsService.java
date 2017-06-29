package rkr.weardndsync;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

public class SettingsService extends WearableListenerService {

    private static final String TAG = "SettingsService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private static final String PATH_DND = "/dnd_switch";
    public static final String WEAR_CALLBACK = "rkr.weardndsync.WEAR_CALLBACK";

    private GoogleApiClient mGoogleApiClient;
    private long mStateTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            registerReceiver(settingsReceiver, filter);
        } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Service.START_NOT_STICKY;
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            registerReceiver(settingsReceiver, filter);
        }

        return Service.START_STICKY;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                if (messageEvent.getData().length == 0)
                    return;

                int state = (int) messageEvent.getData()[0];

                Log.d(TAG, "Target state: " + state);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (state != NotificationManager.INTERRUPTION_FILTER_ALL)
                        state = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                    if (state == (int) notificationManager.getCurrentInterruptionFilter())
                        return;

                    if (notificationManager.isNotificationPolicyAccessGranted())
                        notificationManager.setInterruptionFilter(state);
                } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Intent intent = new Intent(LGHackService.ACTION_SET_STATE);
                    intent.putExtra(LGHackService.EXTRA_STATE, (int) messageEvent.getData()[0]);
                    sendBroadcast(intent);
                } else {
                    AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                    state = state == 4 ?  AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL;
                    //INTERRUPTION_FILTER_ALARMS
                    if (state == audioManager.getRingerMode())
                        return;
                    audioManager.setRingerMode(state);
                }
                return;
            case PATH_DND_REGISTER:
                if (messageEvent.getData().length == 0)
                    return;

                Intent intent = new Intent(WEAR_CALLBACK);
                intent.putExtra("permission", (int) messageEvent.getData()[0]);
                sendBroadcast(intent);
                return;
        }
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        try {
            unregisterReceiver(settingsReceiver);
        } catch (Exception e) {}

        super.onDestroy();
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
        if (capabilityInfo.getNodes().isEmpty())
            return;

        Log.d(TAG, "Watch connected");
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(LGHackService.ACTION_CONNECTED);
            sendBroadcast(intent);
        } else {
            if (mGoogleApiClient.isConnected()) {
                mGoogleApiClient.connect();
            }

            if (mStateTime == 0)
                mStateTime = System.currentTimeMillis();
            int state = getState(this);
            if (state > -1) {
                sendState(mGoogleApiClient, state, mStateTime);
            }
        }
    }

    private int getState(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            int state = mNotificationManager.getCurrentInterruptionFilter();

            Log.d(TAG, "Get state: " + state);
            return state;
        }
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int state = audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL ?  1 : 4;
            //INTERRUPTION_FILTER_ALL / INTERRUPTION_FILTER_ALARMS

            Log.d(TAG, "Get state: " + state);
            return state;
        }

        return -1;
    }

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) || intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                mStateTime = System.currentTimeMillis();
                int state = getState(context);
                if (state > -1)
                    sendState(mGoogleApiClient, state);
            }
        }
    };

    public static void sendState(final GoogleApiClient googleApiClient, final int state) {
        sendState(googleApiClient, state, -1);
    }

    public static void sendState(final GoogleApiClient googleApiClient, final int state, final long timeStamp) {
        if (googleApiClient == null)
            return;

        if (!googleApiClient.isConnected()) {
            googleApiClient.connect();
            return;
        }

        Wearable.NodeApi.getConnectedNodes(googleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                List<Node> nodes = getConnectedNodesResult.getNodes();
                if (nodes == null || nodes.isEmpty()) {
                    Log.d(TAG, "Node not connected");
                    return;
                }

                DataMap config = new DataMap();
                config.putInt("state", state);
                if (timeStamp >= 0)
                    config.putLong("timestamp", timeStamp);
                for (Node node : nodes)
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), PATH_DND, config.toByteArray()).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                        }
                    });
            }
        });
    }
}
