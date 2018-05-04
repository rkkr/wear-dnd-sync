package rkr.weardndsync;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class SettingsService extends WearableListenerService {

    private static final String TAG = "SettingsService";
    public static final String PATH_DND_REGISTER = "/dnd_register";

    public static final String PATH_LOGS = "/dnd_logs";
    public static final String WEAR_CALLBACK_CONNECT = "rkr.weardndsync.WEAR_CALLBACK_CONNECT";
    public static final String WEAR_CALLBACK_LOGS = "rkr.weardndsync.WEAR_CALLBACK_LOGS";

    private GoogleApiClient mGoogleApiClient;

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

        switch (messageEvent.getPath()) {
            case NotificationService.PATH_DND:
                if (messageEvent.getData().length == 0)
                    return;

                int state = (int) messageEvent.getData()[0];

                Log.d(TAG, "Target state: " + state);

                Intent intent = new Intent(NotificationService.ACTION_SET_STATE);
                intent.putExtra(NotificationService.EXTRA_STATE, (int) messageEvent.getData()[0]);
                sendBroadcast(intent);
                return;
            case PATH_DND_REGISTER:
                if (messageEvent.getData().length == 0)
                    return;

                Intent connectIntent = new Intent(WEAR_CALLBACK_CONNECT);
                if (messageEvent.getData().length > 1) {
                    DataMap config = DataMap.fromByteArray(messageEvent.getData());
                    connectIntent.putExtra("permission", config.getBoolean("permission"));
                }
                sendBroadcast(connectIntent);
                Log.d(TAG, "Connected broadcast");
                return;
            case PATH_LOGS:
                if (messageEvent.getData().length == 0)
                    return;

                Intent logIntent = new Intent(WEAR_CALLBACK_LOGS);
                logIntent.putExtra("log", new String(messageEvent.getData()));
                sendBroadcast(logIntent);
                Log.d(TAG, "Logs broadcast");
                return;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is stopped");
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
        if (capabilityInfo.getNodes().isEmpty())
            return;

        Log.d(TAG, "Watch connected");
        Intent intent = new Intent(NotificationService.ACTION_CONNECTED);
        sendBroadcast(intent);
    }
}
