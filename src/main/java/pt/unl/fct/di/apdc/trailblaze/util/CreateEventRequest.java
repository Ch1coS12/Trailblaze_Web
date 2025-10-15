package pt.unl.fct.di.apdc.trailblaze.util;

public class CreateEventRequest {
    public String title;
    public String description;
    public Long dateTimeMillis;
    public String location;
    public String workSheetId;

    public boolean isValid() {
        return title != null && !title.isBlank()
            && dateTimeMillis != null
            && location != null && !location.isBlank()
            && workSheetId != null && !workSheetId.isBlank();
    }
}