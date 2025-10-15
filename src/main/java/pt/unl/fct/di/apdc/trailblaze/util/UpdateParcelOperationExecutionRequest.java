package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.Date;
import java.util.List;

public class UpdateParcelOperationExecutionRequest {
    public String id; // ID da ParcelOperationExecution
    public String parcelId;
    public ParcelExecutionStatus status;
    public Double executedArea;
    public Date startDate;
    public Date lastActivityDate;
    public Date completionDate;

    public List<UpdateActivityRequest> updatedActivities;
}
