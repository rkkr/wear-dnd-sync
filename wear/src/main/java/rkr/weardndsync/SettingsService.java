package rkr.weardndsync;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class SettingsService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "SettingsService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private static final String PATH_DND = "/dnd_switch";
    private GoogleApiClient mGoogleApiClient;
    private String mPhone;
    private int state;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        registerReceiver(settingsReceiver, filter);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (!messageEvent.getPath().equals(PATH_DND_REGISTER))
            return;

        Intent intent= new Intent(this, SettingsService.class);
        startService(intent);

        mPhone = messageEvent.getSourceNodeId();

        if (!mGoogleApiClient.isConnected())
            mGoogleApiClient.blockingConnect(5, TimeUnit.SECONDS);

        Wearable.MessageApi.sendMessage(mGoogleApiClient, mPhone, PATH_DND_REGISTER, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
            }
        });
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(settingsReceiver);
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    private void getPhoneAndSend() {
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
            return;
        }

        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        List<Node> nodes = getConnectedNodesResult.getNodes();

                        if (nodes.isEmpty()) {
                            Log.d(TAG, "Phone not connected");
                            return;
                        }
                        mPhone = nodes.get(0).getId();

                        sendMessage();
                    }
                });
    }

    private void sendMessage() {
        if (mPhone == null) {
            getPhoneAndSend();
            return;
        }

        byte[] data = new byte[] {(byte) state};

        Wearable.MessageApi.sendMessage(mGoogleApiClient, mPhone, PATH_DND, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
            }
        });
    }

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            state = mNotificationManager.getCurrentInterruptionFilter();

            Log.d(TAG, "State: " + state);

            sendMessage();
        }
    };
}
