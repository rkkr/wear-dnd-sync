package rkr.weardndsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

public class MainActivity extends Activity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MainActivity";
    private static final String PATH_DND_REGISTER = "/dnd_register";
    private GoogleApiClient mGoogleApiClient;

    private TextView permissionStatus;
    private TextView watchStatus;
    private TextView watchAppStatus;

    private boolean watchAppFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionStatus = (TextView)findViewById(R.id.textPermissionStatus);
        watchStatus = (TextView)findViewById(R.id.textWatchStatus);
        watchAppStatus = (TextView)findViewById(R.id.textWatchAppStatus);
        Button permissionButton = (Button)findViewById(R.id.buttonRequestPermission);
        Button setupWatchButton = (Button)findViewById(R.id.buttonSetupWatch);
        TextView textLGMessage = (TextView)findViewById(R.id.textLGMessage);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((Build.MANUFACTURER.equals("LGE") || Build.MANUFACTURER.equals("unknown"))) {
                Button permissionButtonLG = (Button)findViewById(R.id.buttonRequestPermissionLG);
                permissionButtonLG.setVisibility(View.VISIBLE);
                textLGMessage.setVisibility(View.VISIBLE);

                permissionButtonLG.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                        startActivity(intent);
                    }
                });

                permissionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setTitle("LG phone detected")
                                .setMessage(R.string.warning_message)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                                        startActivity(intent);
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
            } else {
                permissionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                        startActivity(intent);
                    }
                });
            }
        } else if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
            permissionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                }
            });
            permissionStatus.setVisibility(View.GONE);
            Intent intent = new Intent(this, LGHackService.class);
            startService(intent);
        } else if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            permissionButton.setVisibility(View.GONE);
            permissionStatus.setText("Please enable Notification permission in android settings manually.");
        } else {
            permissionButton.setVisibility(View.GONE);
            permissionStatus.setText("You are running Android version <=4. Ringer mode sync support is experimental.");
        }

        setupWatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), WatchSetupActivity.class);
                startActivity(intent);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();

        registerReceiver(wearCallback, new IntentFilter(SettingsService.WEAR_CALLBACK));

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!watchAppFound)
                    watchAppStatus.setText("Watch application not installed or not running.");
            }
        }, 2000);

        Intent intent = new Intent("rkr.weardndsync.startservice");
        sendBroadcast(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            NotificationManager mNotificationManager = (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);
            if (mNotificationManager.isNotificationPolicyAccessGranted()) {
                permissionStatus.setText("DND permission granted for Phone.");
            } else {
                permissionStatus.setText("To enable synchronization to Phone, please grant DND modification permissions to 'Wear DND Sync' application.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(wearCallback);
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connected");

        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
            new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                    List<Node> nodes = getConnectedNodesResult.getNodes();

                    if (nodes.isEmpty()) {
                        watchStatus.setText("No watches connected.");
                        return;
                    }

                    Wearable.MessageApi.sendMessage(mGoogleApiClient, nodes.get(0).getId(), PATH_DND_REGISTER, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            if(sendMessageResult.getStatus().isSuccess())
                                watchStatus.setText("Watch connected.");
                            else
                                watchStatus.setText("Watch connection failed.");
                        }
                    });
                }
            }
        );
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        watchStatus.setText("Watch connection unavailable.");
    }

    BroadcastReceiver wearCallback = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            watchAppFound = true;
            int permission = intent.getExtras().getInt("permission");
            if (permission == 1) {
                watchAppStatus.setText("Watch app installed, DND permission granted.");
                //PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("service_enabled", true).commit();
                //intent = new Intent("rkr.weardndsync.startservice");
                //sendBroadcast(intent);
            } else {
                watchAppStatus.setText("Watch app installed, DND permission not granted.");
                //PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("service_enabled", false).apply();
            }
        }
    };
}
