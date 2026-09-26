package com.community.cordova.magnetometer;

import android.Manifest;
import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Magnetometer extends CordovaPlugin implements SensorEventListener {

    private static final String LOG_TAG = "Magnetometer";

    // Error codes - matching DeviceOrientation plugin convention
    private static final int ERROR_NOT_AVAILABLE = 3;

    private SensorManager sensorManager;
    private Sensor magnetometer;
    private Sensor rotationVector;

    // Written by execute()/onReset() (WebView / pool threads), read by sensor callbacks (main
    // thread): volatile, and every reader takes ONE local copy - checking the field and then using
    // it again let stopWatch()/onReset() null it in between (NPE in onSensorChanged, Crashlytics).
    private volatile CallbackContext watchCallbackContext;
    private volatile CallbackContext watchHeadingCallbackContext;

    private float[] magnetometerValues = new float[3];

    private int currentAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
    private boolean calibrationNeeded = false;

    private Handler handler;

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
                double headingFilter = args.optDouble(1, 0);
                watchHeading(callbackContext, headingFrequency, headingFilter);
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
                            sensorManager.unregisterListener(listener); // upstream left it registered forever
                            callbackContext.error("Timeout waiting for magnetometer reading");
                        }
                    }
                }, 1000);
            }
        });
    }

    // ---- Heading ------------------------------------------------------------------------------
    // getHeading and watchHeading share ONE sensor listener. Upstream cordova-plugin-device-orientation
    // kept its sensor alive for a few seconds after the last getCurrentHeading so that polling it
    // (the shared compass tab polls every 100 ms) is cheap; this does the same. Sources, best first:
    // TYPE_ROTATION_VECTOR (gyro-fused, tilt-compensated), else accelerometer + magnetometer.

    private static final long HEADING_FRESH_MS = 250;
    private static final long HEADING_LINGER_MS = 3000;
    private static final long HEADING_TIMEOUT_MS = 1000;
    private static final long DECLINATION_TTL_MS = 10 * 60 * 1000;

    private final Object headingLock = new Object();
    private final List<CallbackContext> pendingHeadingCallbacks = new ArrayList<>(); // guarded by headingLock
    private boolean headingSensorsRunning = false;                                   // guarded by headingLock
    private int headingSensorDelay = -1;                                             // guarded by headingLock
    private volatile long lastHeadingRequestAt = Long.MIN_VALUE / 2;
    // {magneticHeading, trueHeading, headingAccuracy, wall-clock timestamp, elapsedRealtime}
    private volatile double[] lastHeading;

    private final Runnable headingIdleStop = new Runnable() {
        @Override
        public void run() {
            maybeStopHeadingSensors();
        }
    };

    private void getHeading(final CallbackContext callbackContext) {
        if (!headingAvailable()) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Heading not available");
            return;
        }
        lastHeadingRequestAt = now();

        final JSONObject cached = freshHeading();
        if (cached != null) {
            callbackContext.success(cached);
            scheduleHeadingIdleStop();
            return;
        }

        synchronized (headingLock) {
            pendingHeadingCallbacks.add(callbackContext);
            if (!headingSensorsRunning) {
                startHeadingSensorsLocked(SensorManager.SENSOR_DELAY_UI);
            }
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean stillWaiting;
                synchronized (headingLock) {
                    stillWaiting = pendingHeadingCallbacks.remove(callbackContext);
                }
                if (stillWaiting) {
                    callbackContext.error("Timeout waiting for heading");
                }
            }
        }, HEADING_TIMEOUT_MS);
        scheduleHeadingIdleStop();
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

    private void watchHeading(CallbackContext callbackContext, final int frequency, final double filter) {
        if (!headingAvailable()) {
            sendError(callbackContext, ERROR_NOT_AVAILABLE, "Heading not available");
            return;
        }

        // A second watchHeading replaces the first (same as before). Set under the lock: the sensor
        // callback decides "nobody is listening, stop" under the same lock.
        synchronized (headingLock) {
            watchHeadingFilter = filter > 0 && !Double.isNaN(filter) ? filter : 0;
            lastWatchHeadingSent = Double.NaN;
            watchHeadingCallbackContext = callbackContext;
            startHeadingSensorsLocked(getSensorDelay(frequency));
        }

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void stopWatchHeading(CallbackContext callbackContext) {
        synchronized (headingLock) {
            watchHeadingCallbackContext = null;
        }
        maybeStopHeadingSensors();
        callbackContext.success();
    }

    /** (Re)registers the heading listener at {@code delay}; no-op if it already runs at that delay. */
    private void startHeadingSensorsLocked(int delay) {
        if (headingSensorsRunning && headingSensorDelay == delay) {
            return;
        }
        sensorManager.unregisterListener(headingListener);
        headingHasMag = false;
        headingHasAccel = false;
        if (rotationVector != null) {
            sensorManager.registerListener(headingListener, rotationVector, delay);
        } else {
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(headingListener, magnetometer, delay);
            if (accelerometer != null) {
                sensorManager.registerListener(headingListener, accelerometer, delay);
            }
        }
        headingSensorsRunning = true;
        headingSensorDelay = delay;
    }

    private void stopHeadingSensorsLocked() {
        sensorManager.unregisterListener(headingListener);
        headingSensorsRunning = false;
        headingSensorDelay = -1;
        headingHasMag = false;
        headingHasAccel = false;
    }

    /** Stops the heading sensors once nobody watches, nobody waits and getHeading has gone quiet. */
    private void maybeStopHeadingSensors() {
        synchronized (headingLock) {
            if (watchHeadingCallbackContext == null && pendingHeadingCallbacks.isEmpty()
                    && now() - lastHeadingRequestAt >= HEADING_LINGER_MS) {
                stopHeadingSensorsLocked();
            }
        }
    }

    private void scheduleHeadingIdleStop() {
        handler.removeCallbacks(headingIdleStop);
        handler.postDelayed(headingIdleStop, HEADING_LINGER_MS);
    }

    private JSONObject freshHeading() {
        final double[] h = lastHeading;
        boolean running;
        synchronized (headingLock) {
            running = headingSensorsRunning;
        }
        if (!running || h == null || now() - (long) h[4] > HEADING_FRESH_MS) {
            return null;
        }
        try {
            return headingJson(h);
        } catch (JSONException e) {
            return null;
        }
    }

    private final float[] headingMagValues = new float[3];
    private final float[] headingAccelValues = new float[3];
    private volatile boolean headingHasMag = false;
    private volatile boolean headingHasAccel = false;
    // watchHeading's filter option (degrees; 0 = every sample) and the heading last sent to the watch
    private double watchHeadingFilter = 0;                   // guarded by headingLock
    private volatile double lastWatchHeadingSent = Double.NaN; // sensor callbacks; reset by watchHeading

    /** Heading needs the rotation-vector sensor, or the magnetometer AND an accelerometer. */
    private boolean headingAvailable() {
        return magnetometer != null
                && (rotationVector != null || sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
    }

    private SensorEventListener headingListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final int type = event.sensor.getType();

            float[] azimuthAndAccuracy = null;
            if (type == Sensor.TYPE_ROTATION_VECTOR) {
                azimuthAndAccuracy = headingFromRotationVector(event.values);
            } else {
                if (type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, headingMagValues, 0, 3);
                    headingHasMag = true;
                } else if (type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, headingAccelValues, 0, 3);
                    headingHasAccel = true;
                }
                if (headingHasMag && headingHasAccel) {
                    azimuthAndAccuracy = headingFromAccelMag(headingAccelValues, headingMagValues);
                }
            }

            List<CallbackContext> waiting = null;
            final CallbackContext watch;
            final double filter;
            synchronized (headingLock) {
                // read under the lock: watchHeading sets it under the same lock, so a watch that
                // starts right now is either seen here or starts the sensors again after this stop
                watch = watchHeadingCallbackContext;
                filter = watchHeadingFilter;
                if (azimuthAndAccuracy != null && !pendingHeadingCallbacks.isEmpty()) {
                    waiting = new ArrayList<>(pendingHeadingCallbacks);
                    pendingHeadingCallbacks.clear();
                }
                if (watch == null && waiting == null && pendingHeadingCallbacks.isEmpty()
                        && now() - lastHeadingRequestAt >= HEADING_LINGER_MS) {
                    // nobody left (stopWatchHeading / page reload / getHeading gone quiet): stop listening
                    stopHeadingSensorsLocked();
                    return;
                }
            }
            if (azimuthAndAccuracy == null) {
                return;
            }

            final float magnetic = azimuthAndAccuracy[0];
            final float declination = getDeclination();
            final float trueHeading = Float.isNaN(declination) ? magnetic : normalizeDegrees(magnetic + declination);
            final double[] h = {magnetic, trueHeading, azimuthAndAccuracy[1], System.currentTimeMillis(), now()};
            lastHeading = h;

            try {
                final double lastSent = lastWatchHeadingSent;
                if (watch != null && (filter <= 0 || Double.isNaN(lastSent) || angularDistance(magnetic, lastSent) >= filter)) {
                    lastWatchHeadingSent = magnetic;
                    PluginResult result = new PluginResult(PluginResult.Status.OK, headingJson(h));
                    result.setKeepCallback(true);
                    watch.sendPluginResult(result);
                }
                if (waiting != null) {
                    for (CallbackContext cb : waiting) {
                        cb.success(headingJson(h));
                    }
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error calculating heading: " + e.getMessage());
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // the rotation vector's status follows the magnetometer's calibration; without this,
            // getAccuracy / isCalibrationNeeded went stale while the heading ran on the rotation vector
            final int type = sensor.getType();
            if (type == Sensor.TYPE_MAGNETIC_FIELD || type == Sensor.TYPE_ROTATION_VECTOR) {
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
                    final double[] h = lastHeading; // the last heading, if getHeading/watchHeading ever ran
                    if (h != null) {
                        info.put("heading", headingJson(h));
                    }

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
                        sensorManager.unregisterListener(listener); // no event within 500 ms: don't leak it

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
                            sensorManager.unregisterListener(listener); // upstream left it registered forever
                            callbackContext.error("Timeout waiting for field strength");
                        }
                    }
                }, 1000);
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final CallbackContext callback = watchCallbackContext;
        if (callback == null) {
            // the watch is gone (stopWatch / page reload / activity recreated): stop listening
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            try {
                JSONObject reading = createReadingObject(event.values);
                PluginResult result = new PluginResult(PluginResult.Status.OK, reading);
                result.setKeepCallback(true);
                callback.sendPluginResult(result);
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

    /** {azimuth 0..360, accuracy in degrees or -1}. Device frame, portrait - same as iOS CLHeading's default. */
    private static float[] headingFromRotationVector(float[] values) {
        float[] r = new float[9];
        SensorManager.getRotationMatrixFromVector(r, values);
        float accuracy = -1;
        // values[4]: estimated heading accuracy in radians, -1 when the sensor does not report it
        if (values.length > 4 && values[4] >= 0) {
            accuracy = (float) Math.toDegrees(values[4]);
        }
        return new float[]{azimuthFromRotationMatrix(r), accuracy};
    }

    private static float[] headingFromAccelMag(float[] accelValues, float[] magValues) {
        float[] r = new float[9];
        float[] i = new float[9];
        if (!SensorManager.getRotationMatrix(r, i, accelValues, magValues)) {
            return null; // free fall or no field: no heading this time (upstream reported 0 = north)
        }
        return new float[]{azimuthFromRotationMatrix(r), -1};
    }

    private static float azimuthFromRotationMatrix(float[] r) {
        float[] orientation = new float[3];
        SensorManager.getOrientation(r, orientation);
        return normalizeDegrees((float) Math.toDegrees(orientation[0]));
    }

    /** Into [0, 360): -1e-6 % 360 + 360 rounds to 360.0f in float, which must read as 0 (north). */
    static float normalizeDegrees(float degrees) {
        float d = degrees % 360f;
        if (d < 0) {
            d += 360f;
        }
        return d >= 360f ? 0f : d;
    }

    /** Smallest angle between two headings, 0..180. */
    static double angularDistance(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static JSONObject headingJson(double[] h) throws JSONException {
        JSONObject heading = new JSONObject();
        heading.put("magneticHeading", h[0]);
        heading.put("trueHeading", h[1]);
        heading.put("headingAccuracy", h[2]);
        heading.put("timestamp", (long) h[3]);
        return heading;
    }

    private float declinationCache = Float.NaN; // main thread only (sensor callbacks)
    private long declinationAt = Long.MIN_VALUE / 2;

    /**
     * Magnetic declination for trueHeading, or NaN. The plugin declares NO location permission: this
     * uses the last known location only when the app already holds one (for its own reasons), and
     * never asks. Without it trueHeading equals magneticHeading, as in upstream device-orientation.
     */
    private float getDeclination() {
        final long t = now();
        if (t - declinationAt < DECLINATION_TTL_MS) {
            return declinationCache;
        }
        declinationAt = t;
        declinationCache = Float.NaN;
        try {
            Context ctx = cordova.getActivity();
            // cordova.hasPermission: Context.checkSelfPermission is API 23, cordova-android 11 still allows 22
            if (!cordova.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    && !cordova.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                return declinationCache;
            }
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return declinationCache;
            }
            Location best = null;
            for (String provider : lm.getProviders(false)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            }
            if (best != null) {
                GeomagneticField field = new GeomagneticField((float) best.getLatitude(), (float) best.getLongitude(),
                        (float) best.getAltitude(), System.currentTimeMillis());
                declinationCache = field.getDeclination();
            }
        } catch (RuntimeException e) { // SecurityException included: permission revoked in between
            Log.w(LOG_TAG, "No declination: " + e.getMessage());
        }
        return declinationCache;
    }

    long now() {
        return SystemClock.elapsedRealtime();
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
        synchronized (headingLock) {
            watchHeadingCallbackContext = null;
            pendingHeadingCallbacks.clear(); // the page that asked is gone
            lastHeadingRequestAt = Long.MIN_VALUE / 2;
            stopHeadingSensorsLocked();
        }
        handler.removeCallbacks(headingIdleStop);
    }

    @Override
    public void onDestroy() {
        onReset();
    }
}
