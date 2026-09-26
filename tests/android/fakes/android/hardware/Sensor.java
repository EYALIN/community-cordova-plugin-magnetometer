package android.hardware;
public class Sensor {
    public static final int TYPE_ACCELEROMETER = 1, TYPE_MAGNETIC_FIELD = 2, TYPE_ROTATION_VECTOR = 11;
    private final int type;
    /** Test hook: runs when the plugin asks the event's sensor for its type (models another thread acting at that moment). */
    public Runnable onGetType;
    public Sensor(int type) { this.type = type; }
    public int rawType() { return type; }
    public int getType() { if (onGetType != null) { Runnable r = onGetType; onGetType = null; r.run(); } return type; }
}
