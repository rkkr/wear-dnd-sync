package rkr.weardndsync;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class NotificationService extends NotificationListenerService {

    private static final String TAG = "NotificationService";
    public static final String ACTION_SET_STATE = "SET_STATE";
    public static final String ACTION_CONNECTED = "CONNECTED";
    public static final String EXTRA_STATE = "STATE";
    public static final String PATH_DND = "/dnd_switch";

    GoogleApiClient mGoogleApiClient;
    private long mStateTime = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
    }

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        Log.d(TAG, "Get state: " + interruptionFilter);

        mStateTime = System.currentTimeMillis();
        sendState(interruptionFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service is started");

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_STATE);
        registerReceiver(settingsReceiver, filter);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
        forceSync();

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is stopped");

        if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();
        try {
            unregisterReceiver(settingsReceiver);
        } catch (Exception e) {}

        super.onDestroy();
    }

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_SET_STATE)) {
                int state = intent.getIntExtra(EXTRA_STATE, 1);
                //INTERRUPTION_FILTER_ALL
                if (state != NotificationListenerService.INTERRUPTION_FILTER_ALL)
                    state = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                if (state == getCurrentInterruptionFilter())
                    return;

                Log.d(TAG, "Set state: " + state);
                requestInterruptionFilter(state);
            }

            if (intent.getAction().equals(ACTION_CONNECTED)) {
                if (mStateTime == 0)
                    mStateTime = System.currentTimeMillis();
                int interruptionFilter = getCurrentInterruptionFilter();
                sendState(interruptionFilter, mStateTime);
                mStateTime = System.currentTimeMillis();
            }
        }
    };

    private void sendState(final int state) {
        sendState(state, -1);
    }

    private void sendState(final int state, final long timeStamp) {
        if (mGoogleApiClient == null) {
            Log.e(TAG, "googleApiClient is null");
            return;
        }

        if (!mGoogleApiClient.isConnected()) {
            Log.e(TAG, "googleApiClient disconnected");
            mGoogleApiClient.connect();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!mGoogleApiClient.isConnected())
                        Log.e(TAG, "googleApiClient reconnect failed");
                    else
                        sendState(state, timeStamp);
                }
            }, 1000);

            return;
        }

        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
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
                    config.putLong("timestamp", timeStamp + 3000);
                for (Node node : nodes)
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), PATH_DND, config.toByteArray()).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                        }
                    });
            }
        });
    }

    private void forceSync() {
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }

        if (mStateTime == 0)
            mStateTime = System.currentTimeMillis();
        int state = getCurrentInterruptionFilter();
        if (state > -1) {
            sendState(state, mStateTime);
        }
        mStateTime = System.currentTimeMillis();
    }
}
