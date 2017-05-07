package rkr.weardndsync;

import android.app.NotificationManager;
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
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class SettingsReceiver extends BroadcastReceiver
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = "SettingsReceiver";
    private static final String PATH_DND = "/dnd_switch";
    private GoogleApiClient mGoogleApiClient;
    private Node mPhone;
    private int state;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        state = mNotificationManager.getCurrentInterruptionFilter();

        Log.d(TAG, "State: " + state);

        if (mGoogleApiClient == null)
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        if (mGoogleApiClient.isConnected())
            sendMessage();
        else
            mGoogleApiClient.connect();
    }

    public static void RegisterReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);

        SettingsReceiver receiver = new SettingsReceiver();
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e)
        { }

        context.registerReceiver(receiver, filter);
    }

    private void getPhone() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
            new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                    List<Node> nodes = getConnectedNodesResult.getNodes();

                    if (nodes.isEmpty()) {
                        Log.d(TAG, "Phone not connected");
                        return;
                    }
                    mPhone = nodes.get(0);

                    sendMessage();
                }
            });
    }

    private void sendMessage() {
        if (mPhone == null) {
            getPhone();
            return;
        }

        byte[] data = new byte[] {(byte) state};

        Wearable.MessageApi.sendMessage(mGoogleApiClient, mPhone.getId(), PATH_DND, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connected");
        sendMessage();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection failed");
    }
}
