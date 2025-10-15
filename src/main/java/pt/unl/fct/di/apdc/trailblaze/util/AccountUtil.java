package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.Entity;

public class AccountUtil {

    public static void addRoleAttributes(Entity.Builder builder, RegisterRequest req) {
        switch (req.getRole()) {
            case "RU":
            case "ADLU":
                builder.set("nif", req.getNif());
                builder.set("cc", req.getCc());
                builder.set("address", req.getAddress());
                builder.set("phone", req.getPhone());
                builder.set("nationality", req.getNationality());
                builder.set("residenceCountry", req.getResidenceCountry());
                break;
            case "PO":
                builder.set("name", req.getFullName());
                builder.set("partner", req.getPartner());
                builder.set("phone", req.getPhone());
                break;
            default:
               
                builder.set("nif", req.getNif());
                builder.set("cc", req.getCc());
                builder.set("address", req.getAddress());
                builder.set("phone", req.getPhone());
                break;
        }
    }
}