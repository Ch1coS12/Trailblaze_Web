package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.Entity;

public class InstitutionalBaseRequest extends CivicRegisterRequest {

	  private String phone;
	    private String address;
	    private String nif;
	    private String cc;
  
    public InstitutionalBaseRequest() {
       
    }

    public InstitutionalBaseRequest(String username,
                                    String email,
                                    String password,
                                    String fullName,
                                    boolean isPublic,
                                    String phone,
                                    String address,
                                    String nif,
                                    String cc) {
        super(username, email, password, fullName, isPublic); 
        this.phone   = phone;
        this.address = address;
        this.nif     = nif;
        this.cc      = cc;
    }

 
    public String getPhone()        { return phone; }
    public void   setPhone(String p){ this.phone = p; }

    public String getAddress()        { return address; }
    public void   setAddress(String a){ this.address = a; }

    public String getNif()        { return nif; }
    public void   setNif(String n){ this.nif = n; }

    public String getCc()        { return cc; }
    public void   setCc(String c){ this.cc = c; }

    public void applyToBuilder(Entity.Builder b) {
        if (phone   != null) b.set("phone",   phone);
        if (address != null) b.set("address", address);
        if (nif     != null) b.set("nif",     nif);
        if (cc      != null) b.set("cc",      cc);
    }
}
