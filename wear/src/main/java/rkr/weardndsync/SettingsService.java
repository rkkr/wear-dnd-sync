package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class SettingsService extends WearableListenerService {

    private static final String TAG = "SettingsService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private static final String PATH_DND = "/dnd_switch";
    public static final String PATH_LOGS = "/dnd_logs";
    private GoogleApiClient mGoogleApiClient;
    public static int targetState = -1;

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

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                DataMap config = DataMap.fromByteArray(messageEvent.getData());
                targetState = config.getInt("state");

                Log.d(TAG, "Target state: " + targetState);
                if (config.containsKey("timestamp")) {
                    long timeStamp = PreferenceManager.getDefaultSharedPreferences(this).getLong("timestamp", 0);
                    if (timeStamp > config.getLong("timestamp")) {
                        Log.d(TAG, "Local state is newer, sync back");
                        sendState(mNotificationManager.getCurrentInterruptionFilter());
                        return;
                    }
                }

                if (targetState != NotificationManager.INTERRUPTION_FILTER_ALL)
                    targetState = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                if (targetState == (int) mNotificationManager.getCurrentInterruptionFilter())
                    return;

                if (mNotificationManager.isNotificationPolicyAccessGranted())
                    mNotificationManager.setInterruptionFilter(targetState);
                return;
            case PATH_DND_REGISTER:
                if (!mGoogleApiClient.isConnected())
                    mGoogleApiClient.connect();

                DataMap data = new DataMap();
                data.putBoolean("permission", mNotificationManager.isNotificationPolicyAccessGranted());

                Wearable.MessageApi.sendMessage(mGoogleApiClient, messageEvent.getSourceNodeId(), PATH_DND_REGISTER, data.toByteArray()).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                    }
                });
                return;
            case PATH_LOGS:
                if (!mGoogleApiClient.isConnected())
                    mGoogleApiClient.connect();

                StringBuilder logs = readLogs();

                Wearable.MessageApi.sendMessage(mGoogleApiClient, messageEvent.getSourceNodeId(), PATH_LOGS, logs.toString().getBytes()).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                    }
                });
                return;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int state = intent.getExtras().getInt("state", -1);
        if (state == -1)
            return START_NOT_STICKY;

        sendState(state);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    private void sendState(final int state) {
        if (mGoogleApiClient == null) {
            Log.e(TAG, "googleApiClient is null");
            return;
        }

        if (!mGoogleApiClient.isConnected()) {
            Log.e(TAG, "googleApiClient disconnected");
            mGoogleApiClient.connect();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!mGoogleApiClient.isConnected())
                        Log.e(TAG, "googleApiClient reconnect failed");
                    else
                        sendState(state);
                }
            }, 1000);

            return;
        }

        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                List<Node> nodes = getConnectedNodesResult.getNodes();
                if (nodes == null || nodes.isEmpty()) {
                    Log.d(TAG, "Node not connected");
                    return;
                }

                byte[] data = new byte[]{(byte) state};
                for (Node node : nodes)
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), PATH_DND, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "Send state: " + sendMessageResult.getStatus().getStatusMessage());
                        }
                    });
            }
        });
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
