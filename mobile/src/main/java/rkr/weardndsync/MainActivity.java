package rkr.weardndsync;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class MainActivity extends Activity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MainActivity";
    private GoogleApiClient mGoogleApiClient;

    private TextView permissionStatus;
    private TextView watchStatus;
    private TextView watchAppStatus;

    private boolean watchAppFound = false;
    private StringBuilder appLogs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setTitle(getResources().getString(R.string.app_name));

        permissionStatus = (TextView)findViewById(R.id.textPermissionStatus);
        watchStatus = (TextView)findViewById(R.id.textWatchStatus);
        watchAppStatus = (TextView)findViewById(R.id.textWatchAppStatus);
        Button permissionButton = (Button)findViewById(R.id.buttonRequestPermission);
        Button setupWatchButton = (Button)findViewById(R.id.buttonSetupWatch);
        Button sendLogsButton = (Button)findViewById(R.id.buttonSendLogs);
        Switch persistentServiceSwitch = (Switch)findViewById(R.id.persistentService);
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

        sendLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ProgressDialog pd = new ProgressDialog(v.getContext());
                pd.setMessage("Gathering logs");
                pd.show();

                appLogs = readLogs();

                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                        new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                            @Override
                            public void onResult(@NonNull NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                                for (Node node : getConnectedNodesResult.getNodes())
                                    Wearable.MessageApi.sendMessage(mGoogleApiClient, node.getId(), SettingsService.PATH_LOGS, null);
                            }
                        }
                );

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pd.hide();
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, appLogs.toString());
                        sendIntent.setType("text/plain");
                        startActivity(sendIntent);
                    }
                }, 3000);
            }
        });

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        persistentServiceSwitch.setChecked(pref.getBoolean("persistent", false));
        persistentServiceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "Persistent switch clicked: " + isChecked);
                pref.edit().putBoolean("persistent", isChecked).commit();
                Intent intent = new Intent("rkr.weardndsync.startservice");
                sendBroadcast(intent);
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();

        IntentFilter callbackFilter = new IntentFilter();
        callbackFilter.addAction(SettingsService.WEAR_CALLBACK_CONNECT);
        callbackFilter.addAction(SettingsService.WEAR_CALLBACK_LOGS);
        registerReceiver(wearCallback, callbackFilter);

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

                    Wearable.MessageApi.sendMessage(mGoogleApiClient, nodes.get(0).getId(), SettingsService.PATH_DND_REGISTER, null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
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
            Log.d(TAG, "Callback to UI: " + intent.getAction());
            if (intent.getAction().equals(SettingsService.WEAR_CALLBACK_CONNECT)) {
                watchAppFound = true;
                boolean permission = intent.getBooleanExtra("permission", false);
                if (permission) {
                    watchAppStatus.setText("Watch app installed, DND permission granted.");
                } else {
                    watchAppStatus.setText("Watch app installed, DND permission not granted.");
                }
            }
            else if (intent.getAction().equals(SettingsService.WEAR_CALLBACK_LOGS)) {
                String watchLog = intent.getStringExtra("log");
                if (appLogs != null)
                    appLogs.append(watchLog);
            }
        }
    };

    private static StringBuilder readLogs() {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("Phone logs:\n");
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
