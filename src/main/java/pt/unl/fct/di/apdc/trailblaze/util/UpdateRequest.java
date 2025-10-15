package pt.unl.fct.di.apdc.trailblaze.util;

public class UpdateRequest {
    public String fullName;
    public String address;
    public String phone;
    public String nationality;
    public String residenceCountry;
    public String nif;
    public String cc;
    public Boolean isPublic;

    public UpdateRequest() {}

    public boolean isValid() {
        return fullName != null || address != null || phone != null ||
               nationality != null || residenceCountry != null ||
               nif != null || cc != null || isPublic != null;
    }
}
