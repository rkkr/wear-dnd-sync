package rkr.weardndsync;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class HackService extends NotificationListenerService {

    private static final String TAG = "NotificationService";
    public static final String ACTION_SET_STATE = "SET_STATE";
    public static final String EXTRA_STATE = "STATE";
    public static final String EXTRA_TIME = "TIME";
    public static final String PATH_DND = "/dnd_switch";

    private long mStateTime = 0;
    private int mLastState = -1;

    @Override
    public void onCreate() {
        Log.d(TAG, "Service is created");

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_STATE);
        registerReceiver(settingsReceiver, filter);

        PackageManager p = getPackageManager();
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        p.setComponentEnabledSetting(componentName,PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is stopped");
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
                if (mLastState == state)
                    //We've just set this state
                    return;

                if (intent.hasExtra(EXTRA_TIME) && mStateTime > intent.getLongExtra(EXTRA_TIME, -1)) {
                    Log.d(TAG, "Local state is newer, sync back");
                    sendState(getCurrentInterruptionFilter());
                    return;
                }
                if (state != NotificationListenerService.INTERRUPTION_FILTER_ALL)
                    state = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                int currentState = getCurrentInterruptionFilter();
                if (currentState != NotificationListenerService.INTERRUPTION_FILTER_ALL)
                    currentState = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                if (state == currentState)
                    return;

                Log.d(TAG, "Set state: " + state);
                mLastState = state;
                requestInterruptionFilter(state);
            }
        }
    };

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        if (mLastState == interruptionFilter)
            //We've just set this state
            return;
        Log.d(TAG, "Get state: " + interruptionFilter);

        mStateTime = System.currentTimeMillis();
        mLastState = interruptionFilter;
        sendState(interruptionFilter);
    }

    private void sendState(final int state) {
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
                for (Node node : nodes) {
                    Wearable.getMessageClient(context).sendMessage(node.getId(), PATH_DND, config.toByteArray());
                }
            }
        });
    }
}
