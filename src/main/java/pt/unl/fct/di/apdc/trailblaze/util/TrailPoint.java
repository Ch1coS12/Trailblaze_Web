package pt.unl.fct.di.apdc.trailblaze.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrailPoint {
    public double latitude;
    public double longitude;
    public long timestamp;      // receber o JSON “timestamp” (ms since epoch)
    public Double altitude;     // receber o JSON “altitude”, se existir

    public TrailPoint() {}

    public boolean isValid() {
        return latitude  >= -90 && latitude  <=  90
            && longitude >= -180 && longitude <= 180;
        // podes também validar timestamp>0, etc.
    }
}
