package rkr.weardndsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.service.notification.NotificationListenerService;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class NotificationService extends NotificationListenerService implements
        CapabilityClient.OnCapabilityChangedListener {

    private static final String TAG = "NotificationService";
    public static final String ACTION_SET_STATE = "SET_STATE";
    public static final String EXTRA_STATE = "STATE";
    public static final String PATH_DND = "/dnd_switch";

    public static boolean serviceStarted = false;
    private long mStateTime = 0;

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        Log.d(TAG, "Get state: " + interruptionFilter);

        mStateTime = System.currentTimeMillis();
        sendState(interruptionFilter);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Service is created");

        serviceStarted = true;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_STATE);
        registerReceiver(settingsReceiver, filter);

        Wearable.getCapabilityClient(this).addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is stopped");

        serviceStarted = false;

        Wearable.getCapabilityClient(this).removeListener(this);
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
                if (state != NotificationListenerService.INTERRUPTION_FILTER_ALL)
                    state = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                int currentState = getCurrentInterruptionFilter();
                if (currentState != NotificationListenerService.INTERRUPTION_FILTER_ALL)
                    currentState = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                if (state == currentState)
                    return;

                Log.d(TAG, "Set state: " + state);
                requestInterruptionFilter(state);
            }
        }
    };

    private void sendState(final int state) {
        sendState(state, -1);
    }

    private void sendState(final int state, final long timeStamp) {
        final Context context = this;
        Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(new OnSuccessListener<List<Node>>() {
            @Override
            public void onSuccess(List<Node> nodes) {
                if (nodes == null || nodes.isEmpty()) {
                    Log.d(TAG, "Node not connected");
                    return;
                }

                DataMap config = new DataMap();
                config.putInt("state", state);
                if (timeStamp >= 0)
                    config.putLong("timestamp", timeStamp + 3000);
                for (Node node : nodes) {
                    Wearable.getMessageClient(context).sendMessage(node.getId(), PATH_DND, config.toByteArray());
                }
            }
        });
    }

    @Override
    public void onCapabilityChanged(@NonNull CapabilityInfo capabilityInfo) {
        if (capabilityInfo.getNodes() == null || capabilityInfo.getNodes().isEmpty())
            return;

        if (mStateTime == 0)
            mStateTime = System.currentTimeMillis();
        int state = getCurrentInterruptionFilter();
        if (state > -1) {
            sendState(state, mStateTime);
        }
        mStateTime = System.currentTimeMillis();
    }
}
