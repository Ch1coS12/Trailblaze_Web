package pt.unl.fct.di.apdc.trailblaze.util;

public class ActivateRequest {
    public String username;
    public String token;

    public ActivateRequest() {}

    public ActivateRequest(String username, String token) {
        this.username = username;
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
