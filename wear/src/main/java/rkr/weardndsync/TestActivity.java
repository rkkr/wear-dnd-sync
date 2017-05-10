package rkr.weardndsync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class TestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        Intent intent= new Intent(this, SettingsService.class);
        startService(intent);

        /*NotificationManager mNotificationManager = (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);
        if(mNotificationManager.isNotificationPolicyAccessGranted()) {
            Log.d("JUHU", "JUHU");
            mNotificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        }
        else
            Log.d("BUUU", "BUUU");*/

        //final AudioManager mode = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        //mode.setRingerMode(AudioManager.RINGER_MODE_SILENT);

        //adb shell settings put secure enabled_notification_listeners com.google.android.wearable.app/com.google.android.clockwork.stream.NotificationCollectorService:rkr.weardndsync/rkr.weardndsync.HackService
    }
}
