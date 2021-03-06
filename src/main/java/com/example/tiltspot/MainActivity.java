package com.example.tiltspot;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // System sensor manager instance.
    private SensorManager mSensorManager;
    // Accelerometer and magnetometer sensors, as retrieved from the
    // sensor manager.
    private Sensor mSensorAccelerometer;
    private Sensor mSensorMagnetometer;
    // TextViews to display current sensor values.
    private TextView mTextSensorAzimuth;
    private TextView mTextSensorPitch;
    private TextView mTextSensorRoll;
    // Very small values for the accelerometer (on all three axes) should
    // be interpreted as 0. This value is the amount of acceptable
    // non-zero drift.
    private static final float VALUE_DRIFT = 0.05f;
    private float[] mAccelerometerData = new float[3];
    private float[] mMagnetometerData = new float[3];
    // imageView objects
    private ImageView mSpotTop;
    private ImageView mSpotBottom;
    private ImageView mSpotLeft;
    private ImageView mSpotRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Lock the orientation to portrait (for now)
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        mTextSensorAzimuth = findViewById(R.id.value_azimuth);
        mTextSensorPitch = findViewById(R.id.value_pitch);
        mTextSensorRoll = findViewById(R.id.value_roll);
        mSpotTop = findViewById(R.id.spot_top);
        mSpotBottom = findViewById(R.id.spot_bottom);
        mSpotLeft = findViewById(R.id.spot_left);
        mSpotRight = findViewById(R.id.spot_right);

        // Get accelerometer and magnetometer sensors from the sensor manager.
        // The getDefaultSensor() method returns null if the sensor
        // is not available on the device.
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
    }

    /**
     * Listeners for the sensors are registered in this callback so that
     * they can be unregistered in onStop().
     */
    @Override
    protected void onStart() {
        super.onStart();
        // Listeners for the sensors are registered in this callback and
        // can be unregistered in onStop().
        //
        // Check to ensure sensors are available before registering listeners.
        // Both listeners are registered with a "normal" amount of delay
        // (SENSOR_DELAY_NORMAL).
        if (mSensorAccelerometer != null) {
            mSensorManager.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mSensorMagnetometer != null) {
            mSensorManager.registerListener(this, mSensorMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister all sensor listeners in this callback so they don't
        // continue to use resources when the app is stopped.
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        int sensorType = sensorEvent.sensor.getType();
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                mAccelerometerData = sensorEvent.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mMagnetometerData = sensorEvent.values.clone();
                break;
            default:
                break;
        }
        SensorChangeAsyncTask asyncTask = new SensorChangeAsyncTask(mAccelerometerData, mMagnetometerData, this);
        OrientationHelper orientationHelper = new OrientationHelper();
        try {
            orientationHelper = asyncTask.execute().get();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // update textViews
        mTextSensorAzimuth.setText(getResources().getString(R.string.value_format, orientationHelper.getAzimuth()));
        mTextSensorPitch.setText(getResources().getString(R.string.value_format, orientationHelper.getPitch()));
        mTextSensorRoll.setText(getResources().getString(R.string.value_format, orientationHelper.getRoll()));
        // reset alpha values to 0 so that if sensor readings change quickly, colors also change
        mSpotTop.setAlpha(0f);
        mSpotBottom.setAlpha(0f);
        mSpotLeft.setAlpha(0f);
        mSpotRight.setAlpha(0f);

        // set alpha colors
        if (orientationHelper.getPitch() > 0) {
            mSpotBottom.setAlpha(orientationHelper.getPitch());
        } else {
            mSpotTop.setAlpha(Math.abs(orientationHelper.getPitch()));
        }
        if (orientationHelper.getRoll() > 0) {
            mSpotLeft.setAlpha(orientationHelper.getRoll());
        } else {
            mSpotRight.setAlpha(Math.abs(orientationHelper.getRoll()));
        }
    }

    /**
     * Must be implemented to satisfy the SensorEventListener interface;
     * unused in this app.
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    ///////////////////////////////////////////////////////////////////////////
    static class SensorChangeAsyncTask extends AsyncTask<Void, Void, OrientationHelper> {
        float[] accelerometerDataAsync;
        float[] magnetometerDataAsync;
        private Display display;
        float azimuth;
        float pitch;
        float roll;
        private WeakReference<Context> weakContext;

        SensorChangeAsyncTask(float[] accelerometerData, float[] magnetometerData, Context context){
            this.accelerometerDataAsync = accelerometerData;
            this.magnetometerDataAsync = magnetometerData;
            weakContext = new WeakReference<>(context);
        }

        @Override
        protected OrientationHelper doInBackground(Void... voids) {
            WindowManager windowManager = (WindowManager) weakContext.get().getSystemService(WINDOW_SERVICE);
            if (windowManager != null) {
                display = windowManager.getDefaultDisplay();
            }
            // rotation matrix
            float[] rotationMatrix = new float[9];
            boolean rotationOK = SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerDataAsync, magnetometerDataAsync);
// new adjusted matrix
            float[] rotationMatrixAdjusted = new float[9];
            switch (display.getRotation()) {
                case Surface.ROTATION_0:
                    rotationMatrixAdjusted = rotationMatrix.clone();
                    break;
                case Surface.ROTATION_90:
                    SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, rotationMatrixAdjusted);
                    break;
                case Surface.ROTATION_180:
                    SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, rotationMatrixAdjusted);
                    break;
                case Surface.ROTATION_270:
                    SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, rotationMatrixAdjusted);
                    break;
            }
            // orientation angles
            float[] orientationValues = new float[3];
            if (rotationOK) {
                SensorManager.getOrientation(rotationMatrixAdjusted, orientationValues);
            }

            // there are 3 components to orientation
            // azimuth - the direction the device is pointing. 0 is magnetic north
            azimuth = orientationValues[0];
            // pitch - top to bottom tilt of the device. 0 is flat
            pitch = orientationValues[1];
            // left to right tilt of the device. 0 is flat
            roll = orientationValues[2];

            if (Math.abs(pitch) < VALUE_DRIFT) {
                pitch = 0;
            }
            if (Math.abs(roll) < VALUE_DRIFT) {
                roll = 0;
            }
            OrientationHelper helper = new OrientationHelper();
            helper.setAzimuth(azimuth);
            helper.setPitch(pitch);
            helper.setRoll(roll);
            return helper;
        }
    }
}