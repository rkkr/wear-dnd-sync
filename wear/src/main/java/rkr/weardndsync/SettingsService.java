package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
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
    public static final String SYNC_BACK = "rkr.weardndsync.syncback";
    private GoogleApiClient mGoogleApiClient;
    public static int targetState = -1;

    @Override
    public void onCreate() {
        super.onCreate();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                DataMap config = DataMap.fromByteArray(messageEvent.getData());
                targetState = config.getInt("state");

                Log.d(TAG, "Target state: " + targetState);
                if (config.containsKey("timestamp")) {
                    long timeStamp = PreferenceManager.getDefaultSharedPreferences(this).getLong("timestamp", 0);
                    if (timeStamp > config.getLong("timestamp")) {
                        Log.d(TAG, "Local state is newer, sync back");
                        Intent intent = new Intent(SYNC_BACK);
                        sendBroadcast(intent);
                        return;
                    }
                }

                if (targetState != NotificationManager.INTERRUPTION_FILTER_ALL)
                    targetState = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                if (targetState == (int) mNotificationManager.getCurrentInterruptionFilter())
                    return;

                if (mNotificationManager.isNotificationPolicyAccessGranted())
                    mNotificationManager.setInterruptionFilter(targetState);
                return;
            case PATH_DND_REGISTER:
                if (!mGoogleApiClient.isConnected())
                    mGoogleApiClient.connect();

                int permission = mNotificationManager.isNotificationPolicyAccessGranted() ? 1 : 0;
                byte[] data = new byte[]{(byte) permission};

                Wearable.MessageApi.sendMessage(mGoogleApiClient, messageEvent.getSourceNodeId(), PATH_DND_REGISTER, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                    }
                });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mGoogleApiClient.isConnected())
            mGoogleApiClient.connect();

        final int state = intent.getExtras().getInt("state", -1);
        if (state == -1)
            return START_NOT_STICKY;

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
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }
}
