package rkr.weardndsync;

import android.app.Activity;
import android.os.Bundle;

public class TestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        SettingsReceiver.RegisterReceiver(getApplicationContext());
    }
}
