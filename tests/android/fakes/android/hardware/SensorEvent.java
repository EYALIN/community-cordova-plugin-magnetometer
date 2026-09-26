package android.hardware;
public class SensorEvent {
    public Sensor sensor;
    public float[] values;
    public SensorEvent(Sensor s, float[] v) { sensor = s; values = v; }
}
