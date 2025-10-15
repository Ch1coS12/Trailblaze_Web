package pt.unl.fct.di.apdc.trailblaze.util;

public class RegisterRequest {

    /* --- credenciais e dados base --- */
    private String username;
    private String email;
    private String password;
    private String fullName;
    /** role principal escolhido no registo (as restantes são atribuídas no backend) */
    private String role;
    /** perfil público («PUBLICO») ou privado («PRIVADO») */
    private boolean publicProfile;   // <- evita nome começado por “is”

    /* --- atributos opcionais --- */
    private String nif;
    private String cc;
    private String address;
    private String phone;
    private String partner;
    private String nationality;
    private String residenceCountry;

    public RegisterRequest() { }

    /* ---------- getters / setters básicos ---------- */

    public String getUsername()                { return username; }
    public void   setUsername(String username) { this.username = username; }

    public String getEmail()             { return email; }
    public void   setEmail(String email) { this.email = email; }

    public String getPassword()               { return password; }
    public void   setPassword(String password){ this.password = password; }

    public String getFullName()                 { return fullName; }
    public void   setFullName(String fullName)  { this.fullName = fullName; }

    public String getRole()           { return role; }
    public void   setRole(String role){ this.role = role; }

    /* --------- perfil público / privado --------- */

    /** método padrão JavaBeans */
    public boolean isPublic() {
        return publicProfile;
    }
    /** alias de compatibilidade com código existente (pode remover depois de refactor) */
    public boolean getIsPublic() {
        return publicProfile;
    }
    /** novo setter com nome inequívoco */
    public void setPublicProfile(boolean publicProfile) {
        this.publicProfile = publicProfile;
    }
    /** mantém o antigo nome para não partir chamadas já compiladas */
    public void setPublic(boolean publicProfile) {
        this.publicProfile = publicProfile;
    }

    /* ---------- campos opcionais ---------- */

    public String getNif()                 { return nif; }
    public void   setNif(String nif)       { this.nif = nif; }

    public String getCc()                  { return cc; }
    public void   setCc(String cc)         { this.cc = cc; }

    public String getAddress()             { return address; }
    public void   setAddress(String address){ this.address = address; }

    public String getPhone()               { return phone; }
    public void   setPhone(String phone)   { this.phone = phone; }

    public String getPartner()             { return partner; }
    public void   setPartner(String partner){ this.partner = partner; }

    public String getNationality()                   { return nationality; }
    public void   setNationality(String nationality) { this.nationality = nationality; }

    public String getResidenceCountry()                        { return residenceCountry; }
    public void   setResidenceCountry(String residenceCountry) { this.residenceCountry = residenceCountry; }

    /* ---------- validação rápida ---------- */
    public boolean isValid() {
        return username   != null && !username.isBlank() &&
               password   != null && !password.isBlank() &&
               email      != null && !email.isBlank()    &&
               fullName   != null && !fullName.isBlank() &&
               role       != null && !role.isBlank();
    }
}
