package rkr.weardndsync;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;

public class LGHackService extends NotificationListenerService {

    private static final String TAG = "LGHackService";
    public static final String ACTION_SET_STATE = "SET_STATE";
    public static final String ACTION_CONNECTED = "CONNECTED";
    public static final String EXTRA_STATE = "STATE";

    GoogleApiClient mGoogleApiClient;
    private long mStateTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
    }

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.d(TAG, "Get state: " + interruptionFilter);

            mStateTime = System.currentTimeMillis();
            SettingsService.sendState(mGoogleApiClient, interruptionFilter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service is started");

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return Service.START_NOT_STICKY;
        if (android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1)
            return Service.START_NOT_STICKY;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_STATE);
        registerReceiver(settingsReceiver, filter);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();

        int interruptionFilter = getCurrentInterruptionFilter();
        SettingsService.sendState(mGoogleApiClient, interruptionFilter, mStateTime);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (pref.getBoolean("persistent", false)) {

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle(this.getText(R.string.app_name))
                    .setContentText("Notification to prevent app from being killed")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build();

            startForeground(1, notification);
            Log.d(TAG, "Service started in foreground");
        } else {
            stopForeground(true);
            Log.d(TAG, "Service removed from foreground");
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service is stopped");

        if (mGoogleApiClient != null)
            mGoogleApiClient.disconnect();
        try {
            unregisterReceiver(settingsReceiver);
        } catch (Exception e) {}

        super.onDestroy();
    }

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                return;

            if (intent.getAction().equals(ACTION_SET_STATE)) {
                int state = intent.getIntExtra(EXTRA_STATE, 1);
                //INTERRUPTION_FILTER_ALL
                if (state != NotificationListenerService.INTERRUPTION_FILTER_ALL)
                    state = NotificationListenerService.INTERRUPTION_FILTER_PRIORITY;
                if (state == getCurrentInterruptionFilter())
                    return;

                Log.d(TAG, "Set state: " + state);

                //Also force the audio mode for some devices
                //AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                //audioManager.setRingerMode(state == INTERRUPTION_FILTER_ALL ? AudioManager.RINGER_MODE_NORMAL : AudioManager.RINGER_MODE_SILENT);
                //requestInterruptionFilter(state);
            }

            if (intent.getAction().equals(ACTION_CONNECTED)) {
                int interruptionFilter = getCurrentInterruptionFilter();
                SettingsService.sendState(mGoogleApiClient, interruptionFilter, mStateTime);
            }
        }
    };
}
