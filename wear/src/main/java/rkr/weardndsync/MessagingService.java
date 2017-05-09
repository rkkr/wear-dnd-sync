package rkr.weardndsync;

import android.content.Intent;
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

public class MessagingService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks{

    private static final String TAG = "MessagingService";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private GoogleApiClient mGoogleApiClient;
    private String phoneId;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        Log.d(TAG, "onMessageReceived: " + messageEvent);

        if (!messageEvent.getPath().equals(PATH_DND_REGISTER))
            return;

        Intent intent= new Intent(this, SettingsService.class);
        startService(intent);

        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .build();

        phoneId = messageEvent.getSourceNodeId();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Wearable.MessageApi.sendMessage(mGoogleApiClient, phoneId, PATH_DND_REGISTER, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                Log.d(TAG, "Send message: " + sendMessageResult.getStatus().getStatusMessage());
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
}
