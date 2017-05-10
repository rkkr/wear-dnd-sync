package rkr.weardndsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ( PreferenceManager.getDefaultSharedPreferences(context).getBoolean("service_enabled", false)) {
            intent = new Intent(context, SettingsService.class);
            context.startService(intent);
        }
    }
}
