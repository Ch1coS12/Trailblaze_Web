package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.Date;

public class EditOperationRequest {
    public String name;
    public Long predictedEndDateMillis;
    public Integer estimatedDurationMinutes;

    public EditOperationRequest() {}

    public boolean hasValidFields() {
        return name != null || predictedEndDateMillis != null || estimatedDurationMinutes != null;
    }

    public Date getPredictedEndDate() {
        return predictedEndDateMillis != null ? new Date(predictedEndDateMillis) : null;
    }

}