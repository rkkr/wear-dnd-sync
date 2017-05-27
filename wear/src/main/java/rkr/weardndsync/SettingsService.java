package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class SettingsService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks{

    private static final String TAG = "SettingsService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private static final String PATH_DND = "/dnd_switch";
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

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        switch (messageEvent.getPath()) {
            case PATH_DND:
                int state = (int) messageEvent.getData()[0];

                Log.d(TAG, "Target state: " + state);

                if (state != NotificationManager.INTERRUPTION_FILTER_ALL)
                    state = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                if (state == (int) mNotificationManager.getCurrentInterruptionFilter())
                    return;

                if (mNotificationManager.isNotificationPolicyAccessGranted())
                    mNotificationManager.setInterruptionFilter(state);
                return;
            case PATH_DND_REGISTER:
                if (!mGoogleApiClient.isConnected())
                    mGoogleApiClient.connect();

                int permission = mNotificationManager.isNotificationPolicyAccessGranted() ? 1 : 0;
                byte[] data = new byte[]{(byte) permission};

                Wearable.MessageApi.sendMessage(mGoogleApiClient, messageEvent.getSourceNodeId(), PATH_DND_REGISTER, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                    }
                });
        }
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
