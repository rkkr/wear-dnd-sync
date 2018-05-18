package rkr.weardndsync;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SettingsService extends WearableListenerService {

    private static final String TAG = "SettingsService";
    public static final String PATH_DND_REGISTER = "/dnd_register";

    public static final String PATH_LOGS = "/dnd_logs";
    public static final String WEAR_CALLBACK_CONNECT = "rkr.weardndsync.WEAR_CALLBACK_CONNECT";
    public static final String WEAR_CALLBACK_LOGS = "rkr.weardndsync.WEAR_CALLBACK_LOGS";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (messageEvent.getData().length == 0)
            return;


        switch (messageEvent.getPath()) {
            case NotificationService.PATH_DND:
                DataMap config1 = DataMap.fromByteArray(messageEvent.getData());
                int state = config1.getInt("state");

                Log.d(TAG, "Target state: " + state);

                Intent intent = new Intent(NotificationService.ACTION_SET_STATE);
                intent.putExtra(NotificationService.EXTRA_STATE, state);
                sendBroadcast(intent);
                return;
            case PATH_DND_REGISTER:
                DataMap config2 = DataMap.fromByteArray(messageEvent.getData());
                Intent connectIntent = new Intent(WEAR_CALLBACK_CONNECT);
                connectIntent.putExtra("permission", config2.getBoolean("permission"));
                sendBroadcast(connectIntent);
                Log.d(TAG, "Connected broadcast");
                return;
            case PATH_LOGS:
                Intent logIntent = new Intent(WEAR_CALLBACK_LOGS);
                logIntent.putExtra("log", new String(messageEvent.getData()));
                sendBroadcast(logIntent);
                Log.d(TAG, "Logs broadcast");
                return;
        }
    }
}
