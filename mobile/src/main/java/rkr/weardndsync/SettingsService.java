package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

public class SettingsService extends WearableListenerService {

    private static final String TAG = "SettingsService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private static final String PATH_DND = "/dnd_switch";
    public static final String WEAR_CALLBACK = "rkr.weardndsync.WEAR_CALLBACK";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                if (messageEvent.getData().length == 0)
                    return;

                int state = (int) messageEvent.getData()[0];

                Log.d(TAG, "Target state: " + state);

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (state != NotificationManager.INTERRUPTION_FILTER_ALL)
                        state = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                    if (state == (int) notificationManager.getCurrentInterruptionFilter())
                        return;

                    if (notificationManager.isNotificationPolicyAccessGranted())
                        notificationManager.setInterruptionFilter(state);
                } else {
                    AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
                    state = state == 4 ?  AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL;
                    //INTERRUPTION_FILTER_ALARMS
                    if (state == audioManager.getRingerMode())
                        return;
                    audioManager.setRingerMode(state);
                }
                return;
            case PATH_DND_REGISTER:
                if (messageEvent.getData().length == 0)
                    return;

                Intent intent = new Intent(WEAR_CALLBACK);
                intent.putExtra("permission", (int) messageEvent.getData()[0]);
                sendBroadcast(intent);
                return;
        }
    }
}
