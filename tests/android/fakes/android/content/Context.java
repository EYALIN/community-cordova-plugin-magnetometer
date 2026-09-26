package android.content;
import java.util.HashSet;
import java.util.Set;
public class Context {
    public static final String SENSOR_SERVICE = "sensor", LOCATION_SERVICE = "location";
    public Object sensorService, locationService;
    public final Set<String> granted = new HashSet<>();
    public Object getSystemService(String name) {
        return SENSOR_SERVICE.equals(name) ? sensorService : LOCATION_SERVICE.equals(name) ? locationService : null;
    }
    public int checkSelfPermission(String p) { return granted.contains(p) ? 0 : -1; }
}
