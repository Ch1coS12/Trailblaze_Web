package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.Date;

/**
 * Representa uma observação feita num trilho por um utilizador
 */
public class TrailObservation {
    public String username;
    public String observation;
    public Date timestamp;

    public TrailObservation() {
        this.timestamp = new Date();
    }

    public TrailObservation(String username, String observation) {
        this();
        this.username = username;
        this.observation = observation;
    }

    public boolean isValid() {
        return username != null && !username.trim().isEmpty() &&
               observation != null && !observation.trim().isEmpty();
    }
}