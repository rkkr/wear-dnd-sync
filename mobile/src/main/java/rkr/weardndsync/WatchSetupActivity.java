package rkr.weardndsync;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class WatchSetupActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_setup);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        TextView watchCommand = (TextView) findViewById(R.id.textWatchCommand);
        watchCommand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CharSequence command = getResources().getText(R.string.watch_command);

                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("ADB", command);
                clipboard.setPrimaryClip(clip);

                Toast.makeText(getApplicationContext(), "Copied", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
