package com.community.cordova.magnetometer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Magnetometer extends CordovaPlugin implements SensorEventListener {

    private static final String LOG_TAG = "Magnetometer";

    // Error codes - matching DeviceOrientation plugin convention
    private static final int ERROR_NOT_AVAILABLE = 3;

    private SensorManager sensorManager;
    private Sensor magnetometer;
    private Sensor rotationVector;

    private CallbackContext watchCallbackContext;
    private CallbackContext watchHeadingCallbackContext;

    private float[] magnetometerValues = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientationValues = new float[3];

    private int currentAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
    private boolean calibrationNeeded = false;

    private Handler handler;
    private Runnable watchRunnable;
    private Runnable watchHeadingRunnable;

    @Override
    protected void pluginInitialize() {
        sensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "isAvailable":
                isAvailable(callbackContext);
                return true;
            case "getReading":
                getReading(callbackContext);
                return true;
            case "getHeading":
                getHeading(callbackContext);
                return true;
            case "watchReadings":
                int frequency = args.optInt(0, 100);
                watchReadings(callbackContext, frequency);
                return true;
            case "stopWatch":
                stopWatch(callbackContext);
                return true;
            case "watchHeading":
                int headingFrequency = args.optInt(0, 100);
                watchHeading(callbackContext, headingFrequency);
                return true;
            case "stopWatchHeading":
                stopWatchHeading(callbackContext);
                return true;
            case "getMagnetometerInfo":
                getMagnetometerInfo(callbackContext);
                return true;
            case "getAccuracy":
                getAccuracy(callbackContext);
                return true;
            case "isCalibrationNeeded":
                isCalibrationNeeded(callbackContext);
                return true;
            case "getFieldStrength":
                getFieldStrength(callbackContext);
                return true;
            default:
                return false;
        }
    }

    private void isAvailable(CallbackContext callbackContext) {
        boolean available = magnetometer != null;
        callbackContext.success(available ? 1 : 0);
    }

    private void getReading(final CallbackContext callbackContext) {
        if (magnetometer == null) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Magnetometer not available");
            return;
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                final boolean[] dataReceived = {false};

                SensorEventListener listener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (!dataReceived[0] && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                            dataReceived[0] = true;
                            sensorManager.unregisterListener(this);

                            try {
                                JSONObject reading = createReadingObject(event.values);
                                callbackContext.success(reading);
                            } catch (JSONException e) {
                                callbackContext.error("Failed to create reading: " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        currentAccuracy = accuracy;
                        calibrationNeeded = accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
                    }
                };

                sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI);

                // Timeout after 1 second
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!dataReceived[0]) {
                            dataReceived[0] = true;
                            callbackContext.error("Timeout waiting for magnetometer reading");
                        }
                    }
                }, 1000);
            }
        });
    }

    private void getHeading(final CallbackContext callbackContext) {
        if (magnetometer == null) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Magnetometer not available");
            return;
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                final boolean[] dataReceived = {false};
                final float[] magValues = new float[3];
                final float[] accelValues = new float[3];
                final boolean[] hasMag = {false};
                final boolean[] hasAccel = {false};

                Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

                SensorEventListener listener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (dataReceived[0]) return;

                        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                            System.arraycopy(event.values, 0, magValues, 0, 3);
                            hasMag[0] = true;
                        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                            System.arraycopy(event.values, 0, accelValues, 0, 3);
                            hasAccel[0] = true;
                        }

                        if (hasMag[0] && hasAccel[0]) {
                            dataReceived[0] = true;
                            sensorManager.unregisterListener(this);

                            try {
                                JSONObject heading = calculateHeading(magValues, accelValues);
                                callbackContext.success(heading);
                            } catch (JSONException e) {
                                callbackContext.error("Failed to calculate heading: " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                            currentAccuracy = accuracy;
                            calibrationNeeded = accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
                        }
                    }
                };

                sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI);
                if (accelerometer != null) {
                    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI);
                }

                // Timeout after 1 second
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!dataReceived[0]) {
                            dataReceived[0] = true;
                            callbackContext.error("Timeout waiting for heading");
                        }
                    }
                }, 1000);
            }
        });
    }

    private void watchReadings(CallbackContext callbackContext, final int frequency) {
        if (magnetometer == null) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Magnetometer not available");
            return;
        }

        // Stop existing watch
        if (watchCallbackContext != null) {
            sensorManager.unregisterListener(this, magnetometer);
        }

        watchCallbackContext = callbackContext;

        int sensorDelay = getSensorDelay(frequency);
        sensorManager.registerListener(this, magnetometer, sensorDelay);

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void stopWatch(CallbackContext callbackContext) {
        if (watchCallbackContext != null) {
            sensorManager.unregisterListener(this, magnetometer);
            watchCallbackContext = null;
        }
        callbackContext.success();
    }

    private void watchHeading(CallbackContext callbackContext, final int frequency) {
        if (magnetometer == null) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Magnetometer not available");
            return;
        }

        // Stop existing watch
        if (watchHeadingCallbackContext != null) {
            stopHeadingSensors();
        }

        watchHeadingCallbackContext = callbackContext;

        int sensorDelay = getSensorDelay(frequency);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sensorManager.registerListener(headingListener, magnetometer, sensorDelay);
        if (accelerometer != null) {
            sensorManager.registerListener(headingListener, accelerometer, sensorDelay);
        }

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void stopWatchHeading(CallbackContext callbackContext) {
        stopHeadingSensors();
        watchHeadingCallbackContext = null;
        callbackContext.success();
    }

    private void stopHeadingSensors() {
        sensorManager.unregisterListener(headingListener);
    }

    private final float[] headingMagValues = new float[3];
    private final float[] headingAccelValues = new float[3];
    private boolean headingHasMag = false;
    private boolean headingHasAccel = false;

    private SensorEventListener headingListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (watchHeadingCallbackContext == null) return;

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, headingMagValues, 0, 3);
                headingHasMag = true;
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, headingAccelValues, 0, 3);
                headingHasAccel = true;
            }

            if (headingHasMag && headingHasAccel) {
                try {
                    JSONObject heading = calculateHeading(headingMagValues, headingAccelValues);
                    PluginResult result = new PluginResult(PluginResult.Status.OK, heading);
                    result.setKeepCallback(true);
                    watchHeadingCallbackContext.sendPluginResult(result);
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "Error calculating heading: " + e.getMessage());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                currentAccuracy = accuracy;
                calibrationNeeded = accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
            }
        }
    };

    private void getMagnetometerInfo(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject info = new JSONObject();
                    info.put("isAvailable", magnetometer != null);
                    info.put("accuracy", currentAccuracy);
                    info.put("calibrationNeeded", calibrationNeeded);
                    info.put("platform", "android");

                    if (magnetometer != null) {
                        final boolean[] dataReceived = {false};
                        final JSONObject[] readingObj = {null};

                        SensorEventListener listener = new SensorEventListener() {
                            @Override
                            public void onSensorChanged(SensorEvent event) {
                                if (!dataReceived[0] && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                                    dataReceived[0] = true;
                                    sensorManager.unregisterListener(this);
                                    try {
                                        readingObj[0] = createReadingObject(event.values);
                                    } catch (JSONException e) {
                                        Log.e(LOG_TAG, "Error creating reading: " + e.getMessage());
                                    }
                                    synchronized (readingObj) {
                                        readingObj.notify();
                                    }
                                }
                            }

                            @Override
                            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                        };

                        sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI);

                        synchronized (readingObj) {
                            try {
                                readingObj.wait(500);
                            } catch (InterruptedException e) {
                                // Ignored
                            }
                        }

                        if (readingObj[0] != null) {
                            info.put("reading", readingObj[0]);
                        }
                    }

                    callbackContext.success(info);
                } catch (JSONException e) {
                    callbackContext.error("Failed to get magnetometer info: " + e.getMessage());
                }
            }
        });
    }

    private void getAccuracy(CallbackContext callbackContext) {
        callbackContext.success(currentAccuracy);
    }

    private void isCalibrationNeeded(CallbackContext callbackContext) {
        callbackContext.success(calibrationNeeded ? 1 : 0);
    }

    private void getFieldStrength(final CallbackContext callbackContext) {
        if (magnetometer == null) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Magnetometer not available");
            return;
        }

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                final boolean[] dataReceived = {false};

                SensorEventListener listener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        if (!dataReceived[0] && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                            dataReceived[0] = true;
                            sensorManager.unregisterListener(this);

                            float x = event.values[0];
                            float y = event.values[1];
                            float z = event.values[2];
                            double magnitude = Math.sqrt(x * x + y * y + z * z);

                            callbackContext.success((int) Math.round(magnitude));
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };

                sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI);

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!dataReceived[0]) {
                            dataReceived[0] = true;
                            callbackContext.error("Timeout waiting for field strength");
                        }
                    }
                }, 1000);
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (watchCallbackContext != null && event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            try {
                JSONObject reading = createReadingObject(event.values);
                PluginResult result = new PluginResult(PluginResult.Status.OK, reading);
                result.setKeepCallback(true);
                watchCallbackContext.sendPluginResult(result);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error sending reading: " + e.getMessage());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            currentAccuracy = accuracy;
            calibrationNeeded = accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
        }
    }

    private JSONObject createReadingObject(float[] values) throws JSONException {
        float x = values[0];
        float y = values[1];
        float z = values[2];
        double magnitude = Math.sqrt(x * x + y * y + z * z);

        JSONObject reading = new JSONObject();
        reading.put("x", x);
        reading.put("y", y);
        reading.put("z", z);
        reading.put("magnitude", magnitude);
        reading.put("timestamp", System.currentTimeMillis());

        return reading;
    }

    private JSONObject calculateHeading(float[] magValues, float[] accelValues) throws JSONException {
        float[] R = new float[9];
        float[] I = new float[9];

        boolean success = SensorManager.getRotationMatrix(R, I, accelValues, magValues);

        float azimuth = 0;
        if (success) {
            float[] orientation = new float[3];
            SensorManager.getOrientation(R, orientation);
            azimuth = (float) Math.toDegrees(orientation[0]);
            if (azimuth < 0) {
                azimuth += 360;
            }
        }

        JSONObject heading = new JSONObject();
        heading.put("magneticHeading", azimuth);
        heading.put("trueHeading", azimuth); // True heading requires GPS, using magnetic as fallback
        heading.put("headingAccuracy", -1); // Not available on Android
        heading.put("timestamp", System.currentTimeMillis());

        return heading;
    }

    private int getSensorDelay(int frequencyMs) {
        if (frequencyMs <= 20) {
            return SensorManager.SENSOR_DELAY_FASTEST;
        } else if (frequencyMs <= 60) {
            return SensorManager.SENSOR_DELAY_GAME;
        } else if (frequencyMs <= 200) {
            return SensorManager.SENSOR_DELAY_UI;
        } else {
            return SensorManager.SENSOR_DELAY_NORMAL;
        }
    }

    /**
     * Send a structured error with code and message
     */
    private void sendError(CallbackContext callbackContext, int code, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            callbackContext.error(error);
        } catch (JSONException e) {
            callbackContext.error(message);
        }
    }

    /**
     * Send a structured error with code and message, keeping callback alive
     */
    private void sendErrorKeepCallback(CallbackContext callbackContext, int code, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, error);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        } catch (JSONException e) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, message);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        }
    }

    @Override
    public void onReset() {
        if (watchCallbackContext != null) {
            sensorManager.unregisterListener(this, magnetometer);
            watchCallbackContext = null;
        }
        stopHeadingSensors();
        watchHeadingCallbackContext = null;
    }

    @Override
    public void onDestroy() {
        onReset();
    }
}
