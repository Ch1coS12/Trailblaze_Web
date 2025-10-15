package pt.unl.fct.di.apdc.trailblaze.util;

public class CreateExecutionSheetRequest {
    public String title;
    public String description;
    public String associatedWorkSheetId;

    public boolean isValid() {
        return title != null && !title.trim().isEmpty()
                && associatedWorkSheetId != null && !associatedWorkSheetId.trim().isEmpty();
    }

}