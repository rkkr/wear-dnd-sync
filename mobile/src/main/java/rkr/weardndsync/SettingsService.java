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
    private int mState;
    private boolean started = false;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            registerReceiver(settingsReceiver, filter);
        } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Service.START_NOT_STICKY;
            //will be sticky when connect event is added
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            registerReceiver(settingsReceiver, filter);
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();

        started = true;

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
                    Intent lgHackServiceIntent = new Intent(this, LGHackService.class);
                    lgHackServiceIntent.setAction(LGHackService.ACTION_SET_STATE);
                    lgHackServiceIntent.putExtra(LGHackService.EXTRA_STATE, state);
                    this.startService(lgHackServiceIntent);
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
        if (started) {
            unregisterReceiver(settingsReceiver);
            mGoogleApiClient.disconnect();
            started = false;
        }
        super.onDestroy();
    }

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && intent.getAction().equals(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) {
                NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                mState = mNotificationManager.getCurrentInterruptionFilter();

                Log.d(TAG, "Get state: " + mState);

                sendState(mGoogleApiClient, mState);
            }
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                mState = audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL ?  1 : 4;
                //INTERRUPTION_FILTER_ALL / INTERRUPTION_FILTER_ALARMS

                Log.d(TAG, "Get state: " + mState);

                sendState(mGoogleApiClient, mState);
            }
        }
    };

    public static void sendState(final GoogleApiClient googleApiClient, final int state) {
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

                byte[] data = new byte[]{(byte) state};
                for (Node node : nodes)
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), PATH_DND, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                        }
                    });
            }
        });
    }
}
