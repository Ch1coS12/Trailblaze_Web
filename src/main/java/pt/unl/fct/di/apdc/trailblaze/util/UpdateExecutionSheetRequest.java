package pt.unl.fct.di.apdc.trailblaze.util;


import java.util.Date;
import java.util.List;

public class UpdateExecutionSheetRequest {

    public String title;
    public String description;
    public ExecutionSheetState state;
    public Date startDate;
    public Date lastActivityDate;   // <-- Adicionado aqui
    public Date completionDate;
    public String observations;

    public Date predictedEndDate;
    public Long estimatedDurationMinutes;

    public UpdateExecutionSheetRequest() {
        // Constructor vazio para deserialização JSON
    }

    public boolean isValid() {
        return (title != null && !title.trim().isEmpty()) ||
               (description != null && !description.trim().isEmpty()) ||
               (state != null) ||
               (startDate != null) ||
               (lastActivityDate != null) ||   // <-- Validado aqui
               (completionDate != null) ||
               (observations != null) ||
               (predictedEndDate != null) ||
               (estimatedDurationMinutes != null);
    }
}
