package com.community.cordova.magnetometer;

import android.app.Activity;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PLU-232: Magnetometer.java on a plain JVM. Sensor callbacks run on the main thread while
 * stopWatch / onReset run on another one; the fake Sensor.getType() hook runs "the other thread"
 * exactly between the plugin's null check and its use of the callback - the interleaving behind the
 * Crashlytics NPE in onSensorChanged. An exception escaping onSensorChanged is an app crash.
 */
public class MagnetometerTest {
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();

    static final ExecutorService POOL = new AbstractExecutorService() {
        public void execute(Runnable r) { r.run(); }
        public void shutdown() {}
        public List<Runnable> shutdownNow() { return Collections.emptyList(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long l, TimeUnit u) { return true; }
    };

    static class Env {
        final Activity activity = new Activity();
        final SensorManager sm = new SensorManager();
        final LocationManager lm = new LocationManager();
        final Magnetometer plugin = new Magnetometer();
        Env() { this(false); }
        Env(boolean rotationVector) {
            sm.hasRotationVector = rotationVector;
            SensorManager.azimuthRad = 0f;
            activity.sensorService = sm;
            activity.locationService = lm;
            plugin.privateInitialize(new CordovaInterface() {
                public Activity getActivity() { return activity; }
                public ExecutorService getThreadPool() { return POOL; }
            });
        }
        CallbackContext exec(String action, Object... args) throws Exception {
            JSONArray a = new JSONArray();
            for (Object o : args) { a.put(o); }
            CallbackContext cb = new CallbackContext();
            plugin.execute(action, a, cb);
            return cb;
        }
        SensorEvent mag() { return new SensorEvent(sm.magnetic, new float[]{10f, 20f, 30f}); }
        SensorEvent acc() { return new SensorEvent(sm.accel, new float[]{0f, 0f, 9.8f}); }
        SensorEvent rot(float accuracyRad) { return new SensorEvent(sm.rotation, new float[]{0f, 0f, 0f, 1f, accuracyRad}); }
        android.os.Handler handler() throws Exception {
            java.lang.reflect.Field f = Magnetometer.class.getDeclaredField("handler");
            f.setAccessible(true);
            return (android.os.Handler) f.get(plugin);
        }
    }

    interface Body { void run(Env e) throws Exception; }

    static void test(String name, Body body) { test(name, false, body); }

    static void test(String name, boolean rotationVector, Body body) {
        String why = null;
        try { body.run(new Env(rotationVector)); } catch (AssertionError a) { why = a.getMessage(); } catch (Throwable t) { why = "APP CRASH: " + t; }
        if (why == null) { passed++; System.out.println("PASS  " + name); }
        else { failed++; failures.add(name); System.out.println("FAIL  " + name + "\n        -> " + why); }
    }

    static void check(boolean ok, String msg) { if (!ok) { throw new AssertionError(msg); } }

    static int errResults(CallbackContext cb) {
        int n = 0;
        for (PluginResult r : cb.results) { if (r.status == PluginResult.Status.ERROR) { n++; } }
        return n;
    }

    static org.json.JSONObject lastOk(CallbackContext cb) {
        for (int i = cb.results.size() - 1; i >= 0; i--) {
            PluginResult r = cb.results.get(i);
            if (r.status == PluginResult.Status.OK && r.message instanceof org.json.JSONObject) { return (org.json.JSONObject) r.message; }
        }
        return null;
    }

    static int okResults(CallbackContext cb) {
        int n = 0;
        for (PluginResult r : cb.results) { if (r.status == PluginResult.Status.OK) { n++; } }
        return n;
    }

    public static void main(String[] args) {
        test("[PLU-232] a watch delivers readings and keeps the callback", e -> {
            CallbackContext cb = e.exec("watchReadings", 100);
            e.sm.emit(e.mag());
            e.sm.emit(e.mag());
            check(okResults(cb) == 2, "readings: " + cb.results);
        });
        test("[PLU-232] stopWatch landing between the null check and the send (other thread) does not crash onSensorChanged", e -> {
            e.exec("watchReadings", 100);
            SensorEvent ev = e.mag();
            ev.sensor.onGetType = () -> { try { e.exec("stopWatch"); } catch (Exception x) { throw new RuntimeException(x); } };
            e.sm.emit(ev);
        });
        test("[PLU-232] onReset (page reload) mid-event does not crash the heading watch", e -> {
            e.exec("watchHeading", 100);
            e.sm.emit(e.acc());
            SensorEvent ev = e.mag();
            ev.sensor.onGetType = () -> e.plugin.onReset();
            e.sm.emit(ev);
        });
        test("[PLU-232] a sensor event after the callback is gone unregisters the listener", e -> {
            CallbackContext cb = e.exec("watchReadings", 100);
            SensorEvent ev = e.mag();
            ev.sensor.onGetType = () -> { try { e.exec("stopWatch"); } catch (Exception x) { throw new RuntimeException(x); } };
            e.sm.emit(ev);
            e.sm.emit(e.mag());
            check(e.sm.listeners.isEmpty(), "listeners still registered: " + e.sm.listeners.size());
            check(okResults(cb) <= 1, "readings after stop: " + cb.results);
        });

        test("[PLU-235] getHeading answers from accelerometer + magnetometer (no rotation vector)", e -> {
            SensorManager.azimuthRad = (float) Math.toRadians(-90); // west, normalised to 270
            CallbackContext cb = e.exec("getHeading");
            e.sm.emit(e.acc());
            e.sm.emit(e.mag());
            org.json.JSONObject h = lastOk(cb);
            check(h != null, "no heading: " + cb.results);
            check(Math.abs(h.getDouble("magneticHeading") - 270) < 0.01, "heading " + h);
            check(h.getDouble("trueHeading") == h.getDouble("magneticHeading"), "no location permission: true must equal magnetic " + h);
        });
        test("[PLU-235] getHeading prefers TYPE_ROTATION_VECTOR and reports its accuracy in degrees", true, e -> {
            SensorManager.azimuthRad = (float) Math.toRadians(45);
            CallbackContext cb = e.exec("getHeading");
            check(e.sm.registrations.size() == 1 && e.sm.registrations.get(0) == 11, "registered: " + e.sm.registrations);
            e.sm.emit(e.rot((float) Math.toRadians(10)));
            org.json.JSONObject h = lastOk(cb);
            check(h != null && Math.abs(h.getDouble("magneticHeading") - 45) < 0.01, "heading " + cb.results);
            check(Math.abs(h.getDouble("headingAccuracy") - 10) < 0.01, "accuracy " + h);
        });
        test("[PLU-235] polling getHeading every 100 ms reuses the running sensor (no re-registration per call)", true, e -> {
            e.exec("getHeading");
            e.sm.emit(e.rot(-1));
            for (int i = 0; i < 5; i++) {
                SystemClock.now += 100;
                e.sm.emit(e.rot(-1));
                CallbackContext cb = e.exec("getHeading");
                check(okResults(cb) == 1, "poll " + i + " not answered at once: " + cb.results);
            }
            check(e.sm.registrations.size() == 1, "registrations: " + e.sm.registrations);
        });
        test("[PLU-235] a getHeading timeout errors once, unregisters, and a late event does not answer it again", e -> {
            CallbackContext cb = e.exec("getHeading");
            SystemClock.now += 5000;
            e.handler().runDelayed();
            check(errResults(cb) == 1, "timeout: " + cb.results);
            check(e.sm.listeners.isEmpty(), "listener leaked after timeout");
            e.sm.emit(e.acc());
            e.sm.emit(e.mag());
            check(cb.results.size() == 1, "answered twice: " + cb.results);
        });
        test("[PLU-235] getHeading lingers ~3 s, then the idle stop releases the sensor", true, e -> {
            e.exec("getHeading");
            e.sm.emit(e.rot(-1));
            check(!e.sm.listeners.isEmpty(), "stopped too early");
            SystemClock.now += 3000;
            e.handler().runDelayed();
            check(e.sm.listeners.isEmpty(), "still listening after the linger");
        });
        test("[PLU-235] getHeading during a watchHeading does not stop the watch", true, e -> {
            CallbackContext watch = e.exec("watchHeading", 100);
            e.exec("getHeading");
            e.sm.emit(e.rot(-1));
            SystemClock.now += 10000;
            e.handler().runDelayed();
            e.sm.emit(e.rot(-1));
            check(okResults(watch) == 2, "watch stopped: " + watch.results);
            e.exec("stopWatchHeading");
            e.sm.emit(e.rot(-1));
            check(e.sm.listeners.isEmpty(), "still listening after stopWatchHeading");
        });
        test("[PLU-235] trueHeading applies declination only when the app already holds a location permission", e -> {
            e.lm.last = new Location(32.0, 34.8, 1);
            CallbackContext cb = e.exec("getHeading");
            e.sm.emit(e.acc());
            e.sm.emit(e.mag());
            org.json.JSONObject h = lastOk(cb);
            check(h.getDouble("trueHeading") == h.getDouble("magneticHeading"), "used location without permission " + h);
            check(e.lm.reads == 0, "read location without permission");

            Env g = new Env();
            g.activity.granted.add("android.permission.ACCESS_COARSE_LOCATION");
            g.lm.last = new Location(32.0, 34.8, 1);
            CallbackContext cb2 = g.exec("getHeading");
            g.sm.emit(g.acc());
            g.sm.emit(g.mag());
            org.json.JSONObject h2 = lastOk(cb2);
            check(Math.abs(h2.getDouble("trueHeading") - 3.2) < 0.01, "declination not applied " + h2);
        });
        test("[PLU-235] a getReading timeout unregisters its listener (upstream leaked it)", e -> {
            CallbackContext cb = e.exec("getReading");
            e.handler().runDelayed();
            check(errResults(cb) == 1, "timeout: " + cb.results);
            check(e.sm.listeners.isEmpty(), "listener leaked");
        });

        test("[PLU-235] a watchHeading that starts while the lingering sensor is stopping keeps its sensor", true, e -> {
            e.exec("getHeading");
            e.sm.emit(e.rot(-1));
            SystemClock.now += 5000; // getHeading has gone quiet: the next event stops the sensor
            final CallbackContext[] watch = {null};
            SensorEvent ev = e.rot(-1);
            ev.sensor.onGetType = () -> { try { watch[0] = e.exec("watchHeading", 100); } catch (Exception x) { throw new RuntimeException(x); } };
            e.sm.emit(ev);
            e.sm.emit(e.rot(-1));
            check(!e.sm.listeners.isEmpty(), "watch started but the sensor was stopped under it");
            check(okResults(watch[0]) >= 1, "watch got nothing: " + watch[0].results);
        });
        test("[PLU-235] watchHeading honours the filter option (minimum change in degrees)", true, e -> {
            CallbackContext watch = e.exec("watchHeading", 100, 5);
            float[] degs = {10, 12, 14, 16, 359, 3};
            for (float d : degs) {
                SensorManager.azimuthRad = (float) Math.toRadians(d);
                e.sm.emit(e.rot(-1));
            }
            // 10 sent; 12, 14 within 5 deg; 16 sent; 359 sent (17 deg away); 3 within 5 deg of 359 across north
            check(okResults(watch) == 3, "filter: " + watch.results);
        });
        test("[PLU-235] headings stay in [0, 360): a tiny negative azimuth reads as 0, not 360", e -> {
            check(Magnetometer.normalizeDegrees(-1e-6f) == 0f, "got " + Magnetometer.normalizeDegrees(-1e-6f));
            check(Magnetometer.normalizeDegrees(-90f) == 270f, "got " + Magnetometer.normalizeDegrees(-90f));
            check(Magnetometer.normalizeDegrees(360f) == 0f, "got " + Magnetometer.normalizeDegrees(360f));
        });
        test("[PLU-235] no rotation vector and no accelerometer: getHeading / watchHeading fail fast with code 3", e -> {
            e.sm.hasAccelerometer = false;
            CallbackContext cb = e.exec("getHeading");
            CallbackContext w = e.exec("watchHeading", 100);
            check(errResults(cb) == 1 && errResults(w) == 1, "expected NOT_AVAILABLE: " + cb.results + " " + w.results);
            check(cb.results.get(0).message.toString().contains("\"code\":3"), "code: " + cb.results);
            check(e.sm.listeners.isEmpty(), "registered a sensor that can never produce a heading");
        });
        test("[PLU-235] getMagnetometerInfo carries the last heading once one exists", true, e -> {
            e.exec("getHeading");
            e.sm.emit(e.rot(-1));
            CallbackContext cb = e.exec("getMagnetometerInfo");
            org.json.JSONObject info = lastOk(cb);
            check(info != null && info.has("heading"), "info: " + cb.results);
        });

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) { System.exit(1); }
    }
}
