package android.hardware;
import java.util.ArrayList;
import java.util.List;
public class SensorManager {
    public static final int SENSOR_DELAY_FASTEST = 0, SENSOR_DELAY_GAME = 1, SENSOR_DELAY_UI = 2, SENSOR_DELAY_NORMAL = 3;
    public static final int SENSOR_STATUS_ACCURACY_LOW = 1, SENSOR_STATUS_ACCURACY_MEDIUM = 2, SENSOR_STATUS_ACCURACY_HIGH = 3;
    /** Test knob: the azimuth (radians) getOrientation reports. */
    public static float azimuthRad = 0f;
    public final Sensor magnetic = new Sensor(Sensor.TYPE_MAGNETIC_FIELD);
    public final Sensor accel = new Sensor(Sensor.TYPE_ACCELEROMETER);
    public final Sensor rotation = new Sensor(Sensor.TYPE_ROTATION_VECTOR);
    public boolean hasRotationVector = false;
    public boolean hasAccelerometer = true;
    public final List<SensorEventListener> listeners = new ArrayList<>();
    /** every registration: listener + sensor type */
    public final List<Integer> registrations = new ArrayList<>();
    public Sensor getDefaultSensor(int type) {
        if (type == Sensor.TYPE_ROTATION_VECTOR) { return hasRotationVector ? rotation : null; }
        return type == Sensor.TYPE_MAGNETIC_FIELD ? magnetic : type == Sensor.TYPE_ACCELEROMETER ? (hasAccelerometer ? accel : null) : null;
    }
    public boolean registerListener(SensorEventListener l, Sensor s, int delay) { registrations.add(s.rawType()); if (!listeners.contains(l)) { listeners.add(l); } return true; }
    public void unregisterListener(SensorEventListener l) { listeners.remove(l); }
    public void unregisterListener(SensorEventListener l, Sensor s) { listeners.remove(l); }
    /** Deliver one event to every registered listener (the platform does this on the main thread). */
    public void emit(SensorEvent e) { for (SensorEventListener l : new ArrayList<>(listeners)) { l.onSensorChanged(e); } }
    public static boolean getRotationMatrix(float[] R, float[] I, float[] g, float[] m) { return true; }
    public static void getRotationMatrixFromVector(float[] R, float[] v) {}
    public static float[] getOrientation(float[] R, float[] values) { values[0] = azimuthRad; return values; }
}
