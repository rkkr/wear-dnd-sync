package rkr.weardndsync;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class SettingsReceiver extends BroadcastReceiver
        implements GoogleApiClient.ConnectionCallbacks {

    private static final String TAG = "SettingsReceiver";
    private static final String PATH_DND = "/dnd_switch";

    GoogleApiClient mGoogleApiClient;
    int mState;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                mState = notificationManager.getCurrentInterruptionFilter();
            } else {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                mState = audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT ?  4 : 1;
                //INTERRUPTION_FILTER_ALARMS / INTERRUPTION_FILTER_ALL
            }

            Log.d(TAG, "State: " + mState);

            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Wearable.API)
                    .build();

            mGoogleApiClient.registerConnectionCallbacks(this);

            if (mGoogleApiClient.isConnected())
                sendState();
            else
                mGoogleApiClient.connect();
        }
    }

    private void sendState() {
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
            @Override
            public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                List<Node> nodes = getConnectedNodesResult.getNodes();
                if (nodes == null || nodes.isEmpty()) {
                    Log.d(TAG, "Node not connected");
                    return;
                }

                byte[] data = new byte[]{(byte) mState};
                for (Node node : nodes)
                    Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), PATH_DND, data).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
                        }
                    });
            }
        });
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        sendState();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
