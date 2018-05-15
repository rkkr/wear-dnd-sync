package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class SettingsService extends WearableListenerService {

    private static final String TAG = "SettingsService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private static final String PATH_DND = "/dnd_switch";
    public static final String PATH_LOGS = "/dnd_logs";
    public static int targetState = -1;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                DataMap config = DataMap.fromByteArray(messageEvent.getData());
                targetState = config.getInt("state");

                Intent intent = new Intent(HackService.ACTION_SET_STATE);
                intent.putExtra(HackService.EXTRA_STATE, config.getInt("state"));
                if (config.containsKey("timestamp"))
                    intent.putExtra(HackService.EXTRA_TIME, config.getLong("timestamp"));
                sendBroadcast(intent);
                return;
            case PATH_DND_REGISTER:
                DataMap data = new DataMap();
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                data.putBoolean("permission", mNotificationManager.isNotificationPolicyAccessGranted());

                Wearable.getMessageClient(this).sendMessage(messageEvent.getSourceNodeId(), PATH_DND_REGISTER, data.toByteArray());
                return;
            case PATH_LOGS:
                StringBuilder logs = readLogs();

                Wearable.getMessageClient(this).sendMessage(messageEvent.getSourceNodeId(), PATH_LOGS, logs.toString().getBytes());
                return;
        }
    }

    private static StringBuilder readLogs() {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Watch logs:\n");
        logBuilder.append("App version " + BuildConfig.VERSION_CODE + ":\n");
        try {
            Process process = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                logBuilder.append(line + "\n");
            }
        } catch (IOException e) {
        }
        return logBuilder;
    }
}
