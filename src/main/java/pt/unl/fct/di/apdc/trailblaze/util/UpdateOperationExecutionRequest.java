package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.Date;
import java.util.List;

public class UpdateOperationExecutionRequest {
    public String id; // ID da OperationExecution a atualizar
    public String observations;
    public Double expectedTotalArea;
    public Double totalExecutedArea;
    public Double percentExecuted;
    public Date predictedEndDate;
    public Long estimatedDurationMinutes;
    public Date startDate;
    public Date lastActivityDate;
    public Date completionDate;

    public List<UpdateParcelOperationExecutionRequest> updatedParcels;
}
