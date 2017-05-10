package rkr.weardndsync;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
    public static final String SERVICE_STOP = "rkr.weardndsync.SERVICE_STOP";
    private GoogleApiClient mGoogleApiClient;
    private int state;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        filter.addAction(SERVICE_STOP);
        registerReceiver(settingsReceiver, filter);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                int state = (int) messageEvent.getData()[0];

                Log.d(TAG, "Target state: " + state);

                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (state == (int) mNotificationManager.getCurrentInterruptionFilter())
                    return;

                if (mNotificationManager.isNotificationPolicyAccessGranted())
                    mNotificationManager.setInterruptionFilter(state);
                return;
            case PATH_DND_REGISTER:
                Intent intent = new Intent(WEAR_CALLBACK);
                sendBroadcast(intent);
                return;
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(settingsReceiver);
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SERVICE_STOP)) {
                stopSelf();
            }

            if (intent.getAction().equals(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) {
                NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                state = mNotificationManager.getCurrentInterruptionFilter();

                Log.d(TAG, "State: " + state);

                if (!mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }

                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        List<Node> nodes = getConnectedNodesResult.getNodes();
                        if (nodes == null || nodes.isEmpty()) {
                            Log.d(TAG, "Node not connected");
                            return;
                        }

                        byte[] data = new byte[]{(byte) state};
                        for (Node node : nodes)
                            Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), PATH_DND, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                                @Override
                                public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                                    Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                                }
                            });
                    }
                });
            }
        }
    };
}
