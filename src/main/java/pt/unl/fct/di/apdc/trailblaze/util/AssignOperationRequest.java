package pt.unl.fct.di.apdc.trailblaze.util;

public class AssignOperationRequest {
    public String executionSheetId;
    public String name;
    public String parcelId;
    public String operatorId;

    public boolean isValid() {
        return executionSheetId != null && name != null && parcelId != null && operatorId != null;
    }

}