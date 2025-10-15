package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.cloud.datastore.*;

import pt.unl.fct.di.apdc.trailblaze.util.AccountState;
import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

@Path("/account")
public class RemoveRequestResource {

    @PATCH
    @Path("/remove-request")
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestRemoval(@HeaderParam("Authorization") String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Token ausente.").build();
        }

        final String PREFIX = "Bearer ";
        String token = tokenHeader.startsWith(PREFIX) ? tokenHeader.substring(PREFIX.length()) : tokenHeader;

        try {
            var claims = JwtUtil.validateToken(token).getBody();
            String username = claims.getSubject();  // quem está autenticado

            // Todos os utilizadores podem pedir remoção da própria conta
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
            Key accountKey = datastore.newKeyFactory().setKind("Account").newKey(username);
            Entity account = datastore.get(accountKey);

            if (account == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Conta não encontrada.").build();
            }

            String estadoAtual = account.getString("state");
            if (AccountState.A_REMOVER.name().equals(estadoAtual)) {
                return Response.status(Response.Status.CONFLICT)
                        .entity("Conta já está marcada para remoção.").build();
            }

            Entity updated = Entity.newBuilder(account)
                    .set("state", AccountState.A_REMOVER.name())
                    .build();

            datastore.put(updated);

            return Response.ok("{\"message\":\"Conta marcada para remoção.\"}").build();

        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Token inválido.").build();
        }
    }
}
