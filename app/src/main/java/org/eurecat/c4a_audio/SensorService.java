package org.eurecat.c4a_audio;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by AIL on 14/09/2015.
 */
public class SensorService extends Service {
    private static final String TAG = SensorService.class.getSimpleName();
    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());

    private boolean isRunning = false;
    private MediaRecorder mRecorder = null;

    private String casUrlLight = "http://192.168.0.106:8888/sensors/5551dd44571d09f8080150f7/data";
    private String casUrlAudio = "http://192.168.0.106:8888/sensors/5551dd44571d09f8080150f7/data";
    private int sendInterval = 10;

    private boolean soundEnabled = true;
    private boolean lightEnabled = true;

    private int soundMax = 10000;
    private int lightMax = 1000;

    SharedPreferences.OnSharedPreferenceChangeListener listener;

    private Handler mHandler;
    private SendToCAS updater;

    private SensorManager mSensorManager = null;
    private Sensor mLightSensor = null;
    private float mLightQuantity;

    @Override
    public void onCreate() {
        Log.d(TAG, "Service onCreate");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        //prefs.edit().putString(SettingsActivity.KEY_PREF_URL_CAS, casUrl).commit();

        soundMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_SOUND_MAX, soundMax + ""));
        lightMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_LIGHT_MAX, lightMax + ""));
        soundEnabled = prefs.getBoolean(SettingsActivity.KEY_SOUND_ENABLED, soundEnabled);
        lightEnabled = prefs.getBoolean(SettingsActivity.KEY_LIGHT_ENABLED, lightEnabled);

        casUrlLight = prefs.getString(SettingsActivity.KEY_PREF_URL_CAS_LIGHT, casUrlLight);
        casUrlAudio = prefs.getString(SettingsActivity.KEY_PREF_URL_CAS_SOUND, casUrlAudio);
        sendInterval = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_SEND_INTERVAL, sendInterval + ""));

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (key.equals(SettingsActivity.KEY_PREF_URL_CAS_SOUND)) {
                    casUrlAudio = prefs.getString(SettingsActivity.KEY_PREF_URL_CAS_SOUND, casUrlAudio);
                }
                if (key.equals(SettingsActivity.KEY_PREF_URL_CAS_LIGHT)) {
                    casUrlLight = prefs.getString(SettingsActivity.KEY_PREF_URL_CAS_LIGHT, casUrlLight);
                }
                if (key.equals(SettingsActivity.KEY_PREF_SEND_INTERVAL)) {
                    sendInterval = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_SEND_INTERVAL, sendInterval + ""));
                }
                if (key.equals(SettingsActivity.KEY_PREF_SOUND_MAX)) {
                    soundMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_SOUND_MAX, soundMax + ""));
                }
                if (key.equals(SettingsActivity.KEY_PREF_LIGHT_MAX)) {
                    lightMax = Integer.parseInt(prefs.getString(SettingsActivity.KEY_PREF_LIGHT_MAX, lightMax + ""));
                }
                if (key.equals(SettingsActivity.KEY_SOUND_ENABLED)) {
                    soundEnabled = prefs.getBoolean(SettingsActivity.KEY_SOUND_ENABLED, soundEnabled);
                }
                if (key.equals(SettingsActivity.KEY_LIGHT_ENABLED)) {
                    lightEnabled = prefs.getBoolean(SettingsActivity.KEY_LIGHT_ENABLED, lightEnabled);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);

        mHandler = new Handler();
        updater = new SendToCAS();

        start();

        //light
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        // Implement a listener to receive updates
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
//                Log.d(TAG, " ---- " + event.values[0]);
                mLightQuantity = event.values[0];
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.w(TAG, "onAccuracyChanged!!");
            }
        };

        // Register the listener with the light sensor -- choosing
        // one of the SensorManager.SENSOR_DELAY_* constants.
        mSensorManager.registerListener(listener, mLightSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service OnStartCommnad");

        if (!isRunning) {
            Log.d(TAG, "\t - Starting updater");
            mHandler.post(updater);
            isRunning = true;
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");

        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }

        isRunning = false;
    }

    private final IBinder mBinder = new LocalBinder();

    public float getLuminosity() {
        return mLightQuantity;
    }

    public class LocalBinder extends Binder {
        public SensorService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SensorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return mBinder;
    }


    public void start() {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mRecorder.setOutputFile("/dev/null");
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mRecorder.start();
        }
    }

    public double getAudioAmplitude() {
        if (mRecorder != null)
            return mRecorder.getMaxAmplitude();
        else
            return 0;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stopSendToCAS() {
        isRunning = false;
        mHandler.removeCallbacks(updater);
    }

    private class SendToCAS implements Runnable {
        @Override
        public void run() {
            if (soundEnabled) {
                JSONArray jsonArray = new JSONArray();
                JSONObject measure = new JSONObject();

                //@msola: returning values changed to numbers:
                //0: quiet
                //1: normal
                //2: noisy
                String value = "0";
                int amp = (int) getAudioAmplitude();
                if (amp <= soundMax / 3) {
                    value = "0";
                } else if (amp <= 2 * soundMax / 3) {
                    value = "1";
                } else {
                    value = "2";
                }

                try {
                    measure.put("at", sdf.format(Calendar.getInstance().getTime()));
                    measure.put("value", value);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                jsonArray.put(measure);

                Log.i(TAG, "AUDIO: Sending to " + casUrlAudio + " the json: " + jsonArray.toString());

                //Send amplitude to CAS
                new sendJsonToCAS().execute(casUrlAudio, jsonArray.toString());
            }


            if (lightEnabled) {
                JSONArray jsonArray = new JSONArray();
                JSONObject measure = new JSONObject();
                try {
                    measure.put("at", sdf.format(Calendar.getInstance().getTime()));
                    measure.put("value", mLightQuantity);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                jsonArray.put(measure);

                Log.i(TAG, "LIGHT: Sending to " + casUrlLight + " the json: " + jsonArray.toString());

                //Send amplitude to CAS
                new sendJsonToCAS().execute(casUrlLight, jsonArray.toString());
            }

            mHandler.postDelayed(updater, sendInterval * 1000);
        }
    }

    private class sendJsonToCAS extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(String... urls) {
            HttpURLConnection urlConnection;
            String data = urls[1];
            String result = null;
            try {
                //Connect
                urlConnection = (HttpURLConnection) ((new URL(urls[0]).openConnection()));
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestMethod("POST");
                urlConnection.connect();

                //Write
                OutputStream outputStream = urlConnection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
                writer.write(data);
                writer.close();
                outputStream.close();

                //Read
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));

                String line = null;
                StringBuilder sb = new StringBuilder();

                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }

                bufferedReader.close();
                result = sb.toString();

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                //e.printStackTrace();
                Log.w(TAG, "Error sending");
            }
            return result;
        }

        @Override
        protected void onPostExecute(String args) {
            // Do something with data
            //Log.i(TAG, "\t -> Result of send to CAS: " + args);
        }
    }

}
