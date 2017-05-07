package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class MessagingService extends WearableListenerService {

    private static final String TAG = "MessagingService";
    private static final String PATH_DND = "/dnd_switch";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (!messageEvent.getPath().equals(PATH_DND))
            return;

        int state = (int) messageEvent.getData()[0];

        Log.d(TAG, "Target state: " + state);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (state == (int) mNotificationManager.getCurrentInterruptionFilter())
            return;

        if (mNotificationManager.isNotificationPolicyAccessGranted())
            mNotificationManager.setInterruptionFilter(state);
    }
}
