package android.hardware;
/** Fake: declination = latitude / 10 (enough to see it applied). */
public class GeomagneticField {
    private final float lat;
    public GeomagneticField(float lat, float lon, float alt, long time) { this.lat = lat; }
    public float getDeclination() { return lat / 10f; }
}
