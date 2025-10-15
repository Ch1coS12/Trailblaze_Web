package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.cloud.datastore.*;

@Path("/logout")
public class LogoutResource {

    private static final Datastore datastore =
            DatastoreOptions.getDefaultInstance().getService();

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout(@HeaderParam("Authorization") String header) {

        if (header == null || header.isBlank())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token ausente.").build();


        String token = header.replace("Bearer ", "").trim();

        Key key = datastore.newKeyFactory().setKind("Session").newKey(token);
        Entity session = datastore.get(key);

        if (session == null)
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Sessão inexistente ou já terminada.").build();

        datastore.delete(key);
        return Response.ok("{\"message\":\"Logout efetuado com sucesso.\"}").build();
    }
}