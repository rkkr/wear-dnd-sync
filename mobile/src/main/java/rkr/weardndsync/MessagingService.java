package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class MessagingService extends WearableListenerService {

    private static final String TAG = "MessagingService";
    private static final String PATH_DND = "/dnd_switch";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    public static final String WEAR_CALLBACK = "rkr.weardndsync.WEAR_CALLBACK";

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
}
