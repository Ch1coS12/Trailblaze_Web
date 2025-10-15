package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.List;

/**
 * Request para criar um novo trilho
 */
public class CreateTrailRequest {
    public String name;
    public String worksheetId;
    public TrailVisibility visibility;
    public List<TrailPoint> points;

    public CreateTrailRequest() {}

    /**
     * Valida se o request tem os campos obrigatórios
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() &&
        
               points != null && !points.isEmpty() &&
               points.stream().allMatch(TrailPoint::isValid);
    }
}