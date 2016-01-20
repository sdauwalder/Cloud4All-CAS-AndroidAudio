package org.eurecat.c4a_audio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();
    private MainActivity mActivity = this;
    private SensorService mAudioService;

    private boolean mBound = false;

    private Handler mHandler;
    private TakeMeasureRunnable updater;

    private ProgressBar progressBar;
    private ProgressBar progressBar2;

    private Intent audioService;

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    private boolean soundEnabled = true;
    private boolean lightEnabled = true;

    private int soundMax = 10000;
    private int lightMax = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        audioService = new Intent(mActivity, SensorService.class);
        bindService(audioService, mServiceConnection, Context.BIND_AUTO_CREATE);

        mHandler = new Handler();
        updater = new TakeMeasureRunnable();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        soundMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_SOUND_MAX, soundMax + ""));
        lightMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_LIGHT_MAX, lightMax + ""));
        soundEnabled = prefs.getBoolean(SettingsActivity.KEY_SOUND_ENABLED, soundEnabled);
        lightEnabled = prefs.getBoolean(SettingsActivity.KEY_LIGHT_ENABLED, lightEnabled);

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (key.equals(SettingsActivity.KEY_PREF_SOUND_MAX)) {
                    soundMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_SOUND_MAX, soundMax + ""));
                    progressBar.setMax(soundMax);
                }
                if (key.equals(SettingsActivity.KEY_PREF_LIGHT_MAX)) {
                    lightMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_LIGHT_MAX, lightMax + ""));
                    progressBar2.setMax(lightMax);
                }
                if (key.equals(SettingsActivity.KEY_SOUND_ENABLED)) {
                    soundEnabled = prefs.getBoolean(SettingsActivity.KEY_SOUND_ENABLED, soundEnabled);
                    if (!soundEnabled) {
                        progressBar.setProgress(0);
                    }
                }
                if (key.equals(SettingsActivity.KEY_LIGHT_ENABLED)) {
                    lightEnabled = prefs.getBoolean(SettingsActivity.KEY_LIGHT_ENABLED, lightEnabled);
                    if (!lightEnabled) {
                        progressBar2.setProgress(0);
                    }
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);

        ((Button) findViewById(R.id.button_start)).setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(audioService);

                ((Button) findViewById(R.id.button_start)).setEnabled(false);
                ((Button) findViewById(R.id.button_stop)).setEnabled(true);

                // Bind to LocalService
                mHandler.postDelayed(updater, 100);
            }
        });

        ((Button) findViewById(R.id.button_stop)).setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeCallbacks(updater);

                mAudioService.stopSendToCAS();

                ((Button) findViewById(R.id.button_start)).setEnabled(true);
                ((Button) findViewById(R.id.button_stop)).setEnabled(false);

                progressBar.setProgress(0);
                progressBar2.setProgress(0);
            }
        });
        ((CheckBox) findViewById(R.id.checkBox)).setChecked(soundEnabled);
        ((CheckBox) findViewById(R.id.checkBox)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(SettingsActivity.KEY_SOUND_ENABLED, ((CheckBox) v).isChecked()).commit();
            }
        });

        ((CheckBox) findViewById(R.id.checkBox2)).setChecked(lightEnabled);
        ((CheckBox) findViewById(R.id.checkBox2)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean(SettingsActivity.KEY_LIGHT_ENABLED, ((CheckBox) v).isChecked()).commit();
            }
        });

        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        progressBar.setMax(soundMax);

        progressBar2 = (ProgressBar) findViewById(R.id.progressBar2);
        progressBar2.setMax(lightMax);

    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "Service connected");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SensorService.LocalBinder binder = (SensorService.LocalBinder) service;
            mAudioService = binder.getService();
            mBound = true;
            ((Button) findViewById(R.id.button_start)).setEnabled(!mAudioService.isRunning());
            ((Button) findViewById(R.id.button_stop)).setEnabled(mAudioService.isRunning());

            if (mAudioService.isRunning()) {
                mHandler.postDelayed(updater, 100);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "Service disconnected");
            mBound = false;
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        //FeedbackManager.getInstance().detachActivity(this);
        if (mBound)
            unbindService(mServiceConnection);
        mAudioService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class TakeMeasureRunnable implements Runnable {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mAudioService != null) {
                        //Log.i(TAG, "--- " + mAudioService.getLuminosity());
                        if (soundEnabled) {
                            int a = (int) mAudioService.getAudioAmplitude();
                            progressBar.setProgress(a);//(int) mAudioService.getAudioAmplitude());
                        }
                        if (lightEnabled)
                            progressBar2.setProgress((int) mAudioService.getLuminosity());
                    }
                }
            });

            mHandler.postDelayed(updater, 100);
        }
    }
}
