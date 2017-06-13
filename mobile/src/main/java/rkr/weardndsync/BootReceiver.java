package rkr.weardndsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        //if ( !PreferenceManager.getDefaultSharedPreferences(context).getBoolean("service_enabled", false))
        //    return;

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent = new Intent(context, SettingsService.class);
            context.startService(intent);
        } else if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = new Intent(context, LGHackService.class);
            context.startService(intent);
        } else {
            intent = new Intent(context, SettingsService.class);
            context.startService(intent);
        }
    }
}