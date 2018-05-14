package rkr.weardndsync;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

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

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            permissionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    startActivity(intent);
                }
            });
            Intent intent = new Intent(this, NotificationService.class);
            startService(intent);
        } else {
            permissionButton.setVisibility(View.GONE);
            permissionStatus.setText("Please enable Notification permission in android settings manually.");
        }

        setupWatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), WatchSetupActivity.class);
                startActivity(intent);
            }
        });

        final Context context = this;
        sendLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ProgressDialog pd = new ProgressDialog(v.getContext());
                pd.setMessage("Gathering logs");
                pd.show();

                appLogs = readLogs();

                Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(new OnSuccessListener<List<Node>>() {
                    @Override
                    public void onSuccess(List<Node> nodes) {
                        if (nodes == null || nodes.isEmpty()) {
                            Log.d(TAG, "Node not connected");
                            return;
                        }
                        for (Node node : nodes)
                            Wearable.getMessageClient(context).sendMessage(node.getId(), SettingsService.PATH_LOGS, null);
                    }
                });

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, appLogs.toString());
                        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "DND Sync Logs");
                        sendIntent.putExtra(Intent.EXTRA_TITLE, "DND Sync Logs");
                        sendIntent.setType("text/plain");
                        startActivity(sendIntent);
                    }
                }, 3000);
            }
        });

        IntentFilter callbackFilter = new IntentFilter();
        callbackFilter.addAction(SettingsService.WEAR_CALLBACK_CONNECT);
        callbackFilter.addAction(SettingsService.WEAR_CALLBACK_LOGS);
        registerReceiver(wearCallback, callbackFilter);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!watchAppFound) {
                    Log.w(TAG, "Watch application not installed or not running.");
                    watchAppStatus.setText("Watch application not installed or not running.");
                }
            }
        }, 2000);

        Intent intent = new Intent("rkr.weardndsync.startservice");
        sendBroadcast(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (checkNotificationAccessEnabled()) {
            permissionStatus.setText("Notification permission granted for Phone.");
        } else {
            Log.w(TAG, "Phone DND permission not granted");
            permissionStatus.setText("Notification permission not granted for Phone, please grant notification permissions for 'Wear DND Sync'");
        }

        final Context context = this;

        Wearable.getNodeClient(context).getConnectedNodes().addOnSuccessListener(new OnSuccessListener<List<Node>>() {
            @Override
            public void onSuccess(List<Node> nodes) {
                if (nodes.isEmpty()) {
                    watchStatus.setText("No watches connected.");
                    return;
                }
                Wearable.getMessageClient(context).sendMessage(nodes.get(0).getId(), SettingsService.PATH_DND_REGISTER, null).addOnSuccessListener(new OnSuccessListener<Integer>() {
                    @Override
                    public void onSuccess(Integer integer) {
                        watchStatus.setText("Watch connected.");
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        watchStatus.setText("Watch connection failed.");
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                watchStatus.setText("No watches connected.");
            }
        });
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(wearCallback);
        } catch (Exception e) {

        }
        super.onDestroy();
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
                    Log.d(TAG, "Watch DND permission not granted");
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

    private boolean checkNotificationAccessEnabled() {
        try {
            return Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners").contains(getPackageName());
        } catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
