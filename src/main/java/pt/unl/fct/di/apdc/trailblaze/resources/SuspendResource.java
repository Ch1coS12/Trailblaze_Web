package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import com.google.cloud.datastore.*;

import java.util.List;
import java.util.stream.Collectors;

import pt.unl.fct.di.apdc.trailblaze.util.AccountStateChangeRequest;
import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

@Path("/suspend")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SuspendResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();

    /* ------------------------------------------------------------- */
    /*                            POST                               */
    /* ------------------------------------------------------------- */
    @POST
    public Response suspendAccount(AccountStateChangeRequest req,
                                   @HeaderParam("Authorization") String hdr) {

        if (hdr == null || hdr.isBlank())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token ausente.").build();

        String jwt = hdr.replaceFirst("(?i)^Bearer\\s+", "").trim();

        try {
            /* -------- valida JWT + extrai roles do chamador -------- */
            var    claims = JwtUtil.validateToken(jwt).getBody();
            String caller = claims.getSubject();

            @SuppressWarnings("unchecked")
            List<String> callerRoles = claims.containsKey("roles")
                    ? (List<String>) claims.get("roles", List.class)
                    : List.of(claims.get("role", String.class));      // fallback

            if (callerRoles == null ||
               !(callerRoles.contains("SYSADMIN") || callerRoles.contains("SYSBO")))
                return Response.status(Response.Status.FORBIDDEN)
                               .entity("Sem permissões para suspender contas.").build();

            /* ---------------- conta-alvo ---------------- */
            Key      accKey = DS.newKeyFactory().setKind("Account").newKey(req.targetUsername);
            Entity   acc    = DS.get(accKey);

            if (acc == null)
                return Response.status(Response.Status.NOT_FOUND)
                               .entity("Conta não encontrada.").build();

            List<String> targetRoles = acc.contains("roles")
                    ? acc.getList("roles").stream()
                         .map(v -> ((StringValue) v).get())
                         .collect(Collectors.toList())
                    : List.of(acc.getString("role"));                 // fallback

            /* SYSBO não pode suspender SYSADMIN nem SYSBO */
            if (callerRoles.contains("SYSBO") &&
               (targetRoles.contains("SYSADMIN") || targetRoles.contains("SYSBO")))
                return Response.status(Response.Status.FORBIDDEN)
                               .entity("SYSBO não pode suspender esta conta.").build();

            if ("SUSPENSA".equals(acc.getString("state")))
                return Response.status(Response.Status.CONFLICT)
                               .entity("Conta já se encontra suspensa.").build();

            /* ---------------- update ---------------- */
            Entity updated = Entity.newBuilder(acc)
                                   .set("state", "SUSPENSA")
                                   .build();
            DS.put(updated);

            return Response.ok("Conta suspensa com sucesso.").build();

        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token inválido.").build();
        }
    }
}
