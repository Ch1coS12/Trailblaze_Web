package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.List;

public class CreateOccurrenceRequest {
    public String incidentType;
    public String description;
    public List<String> evidenceUrls;

    public boolean isValid() {
        return incidentType != null && !incidentType.isBlank()
            && description  != null && !description.isBlank();
    }
}
