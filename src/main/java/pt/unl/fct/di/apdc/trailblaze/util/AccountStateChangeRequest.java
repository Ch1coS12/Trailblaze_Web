package pt.unl.fct.di.apdc.trailblaze.util;

public class AccountStateChangeRequest {
    public String targetUsername;

    public AccountStateChangeRequest() {}

    public AccountStateChangeRequest(String targetUsername) {
        this.targetUsername = targetUsername;
    }
}
