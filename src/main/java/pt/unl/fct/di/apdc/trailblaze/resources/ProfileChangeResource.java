package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import com.google.cloud.datastore.*;
import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

@Path("/profile")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProfileChangeResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();

    @POST
    public Response toggleProfile(@HeaderParam("Authorization") String header) {

        /* ---------- 1.  Token ---------- */
        if (header == null || header.isBlank())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token ausente.").build();

        String token = header.startsWith("Bearer ") ? header.substring(7).trim()
                                                    : header.trim();

        /* ---------- 2.  Validação ---------- */
        String username;
        java.util.List<String> roles;
        try {
            var body  = JwtUtil.validateToken(token).getBody();
            username  = body.getSubject();

            @SuppressWarnings("unchecked")
            java.util.List<String> tmp = (java.util.List<String>) body.get("roles", java.util.List.class);
            roles = (tmp != null && !tmp.isEmpty())
                    ? tmp                                       // nova claim “roles”
                    : java.util.List.of(body.get("role", String.class));   // fallback contas antigas

        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token inválido.").build();
        }

        /* ---------- 3.  Autorização ---------- */
        if (!roles.contains("RU"))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Apenas utilizadores RU podem alterar o perfil.").build();

        /* ---------- 4.  Carrega & alterna perfil ---------- */
        Key userKey = DS.newKeyFactory().setKind("Account").newKey(username);
        Entity user = DS.get(userKey);
        if (user == null)
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Conta não encontrada.").build();

        String current = user.getString("profile");
        String next    = "PUBLICO".equals(current) ? "PRIVADO" : "PUBLICO";

        Entity updated = Entity.newBuilder(user)
                               .set("profile", next)
                               .build();
        DS.put(updated);

        return Response.ok("Perfil alterado para: " + next).build();
    }
}
