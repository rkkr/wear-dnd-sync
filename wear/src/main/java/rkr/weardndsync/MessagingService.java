package rkr.weardndsync;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class MessagingService extends WearableListenerService {

    private static final String TAG = "MessagingService";
    private static final String PATH_DND_REGISTER = "/dnd_register";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (!messageEvent.getPath().equals(PATH_DND_REGISTER))
            return;

        SettingsReceiver.RegisterReceiver(getApplicationContext());
    }
}
