package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsReceiver extends BroadcastReceiver {

    private static final String TAG = "SettingsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putLong("timestamp", System.currentTimeMillis()).apply();

            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            int state = mNotificationManager.getCurrentInterruptionFilter();
            if (state == SettingsService.targetState && !intent.getAction().equals("rkr.weardndsync.syncback"))
                //This state was set by SettingsService
                return;

            Log.d(TAG, "State: " + state);

            intent = new Intent(context, SettingsService.class);
            intent.putExtra("state", state);
            context.startService(intent);
        }
    }
}
