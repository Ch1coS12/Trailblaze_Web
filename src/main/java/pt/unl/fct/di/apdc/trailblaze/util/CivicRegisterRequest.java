package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.Entity;



public class CivicRegisterRequest extends RegisterRequest {   
   
    public CivicRegisterRequest() { }

  
    public CivicRegisterRequest(String username,
                                String email,
                                String password,
                                String fullName,
                                boolean isPublic) {
        setUsername(username);
        setEmail(email);
        setPassword(password);
        setFullName(fullName);
        setPublic(isPublic);
        setRole("RU");              
    }

   
    public void applyToBuilder(Entity.Builder builder) {
        builder.set("profile", getIsPublic() ? "PUBLICO" : "PRIVADO");
        builder.set("role",    "RU");
        builder.set("state",   "ATIVADA");
        builder.set("registrationType", "CIVIC");
    }
}