package android.location;
import java.util.ArrayList;
import java.util.List;
public class LocationManager {
    public Location last;
    public int reads;
    public List<String> getProviders(boolean enabledOnly) { List<String> l = new ArrayList<>(); l.add("network"); return l; }
    public Location getLastKnownLocation(String provider) { reads++; return last; }
}
