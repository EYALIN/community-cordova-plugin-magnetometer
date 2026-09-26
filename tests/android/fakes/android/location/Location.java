package android.location;
public class Location {
    private final double lat, lon; private final long time;
    public Location(double lat, double lon, long time) { this.lat = lat; this.lon = lon; this.time = time; }
    public double getLatitude() { return lat; } public double getLongitude() { return lon; }
    public double getAltitude() { return 0; } public long getTime() { return time; }
}
