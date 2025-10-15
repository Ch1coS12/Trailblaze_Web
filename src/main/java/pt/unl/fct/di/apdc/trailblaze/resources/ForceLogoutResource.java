package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.cloud.datastore.*;

import pt.unl.fct.di.apdc.trailblaze.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Path("/force-logout")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ForceLogoutResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory ACC_KF = DS.newKeyFactory().setKind("Account");
    private static final KeyFactory JWT_KF = DS.newKeyFactory().setKind("JwtActive");

    /* -------- helpers -------- */
    private static List<String> rolesFromToken(String jwt) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) JwtUtil.validateToken(jwt)
                                                  .getBody()
                                                  .get("roles", List.class);
        // ​retro-compatibilidade: se existir só o claim antigo 'role'
        if (roles == null || roles.isEmpty()) {
            String single = JwtUtil.validateToken(jwt).getBody().get("role", String.class);
            roles = single != null ? List.of(single) : List.of();
        }
        return roles;
    }

    private static List<String> rolesFromEntity(Entity e) {
        if (e.contains("roles"))
            return e.getList("roles").stream()
                    .map(v -> ((StringValue) v).get())
                    .collect(Collectors.toList());
        return List.of(e.getString("role"));   // contas antigas
    }

    /* ===================================================== */

    @POST
    public Response forceLogout(ForceLogoutRequest req,
                                @HeaderParam("Authorization") String auth) {

        /* ---------- 1. autenticação do chamador ---------- */
        if (auth == null || auth.isBlank())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token ausente.").build();

        String token = auth.replaceFirst("(?i)^Bearer\\s+", "").trim();

        List<String> callerRoles;
        try {
            callerRoles = rolesFromToken(token);
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token inválido.").build();
        }

        boolean callerIsSysAdmin = callerRoles.contains("SYSADMIN");
        boolean callerIsSysBo    = callerRoles.contains("SYSBO");

        if (!(callerIsSysAdmin || callerIsSysBo))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Sem permissões.").build();

        /* ---------- 2. validação do alvo ---------- */
        if (req == null || req.targetUsername == null || req.targetUsername.isBlank())
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("targetUsername obrigatório.").build();

        Entity account = DS.get(ACC_KF.newKey(req.targetUsername));
        if (account == null)
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Conta não existe.").build();

        List<String> targetRoles = rolesFromEntity(account);

        if (callerIsSysBo &&
            (targetRoles.contains("SYSADMIN") || targetRoles.contains("SYSBO")))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("SYSBO não pode terminar sessões desse utilizador.").build();

        /* ---------- 3-A. remover sessões HTTP ---------- */
        Query<Entity> sessQ = Query.newEntityQueryBuilder()
                                   .setKind("Session")
                                   .setFilter(StructuredQuery.PropertyFilter
                                           .eq("username", req.targetUsername))
                                   .build();

        List<Key> toDelete = new ArrayList<>();
        DS.run(sessQ).forEachRemaining(e -> toDelete.add(e.getKey()));
        if (!toDelete.isEmpty())
            DS.delete(toDelete.toArray(Key[]::new));

        /* ---------- 3-B. revogar JWT ---------- */
        if (req.targetJwt != null && !req.targetJwt.isBlank()) {
            // revoga apenas o JWT indicado
            try {
                var claims = JwtUtil.validateToken(req.targetJwt).getBody();
                if (!req.targetUsername.equals(claims.getSubject()))
                    return Response.status(Response.Status.BAD_REQUEST)
                                   .entity("JWT não pertence ao utilizador alvo.").build();

                String jti  = claims.getId();
                long   exp  = claims.getExpiration().getTime();
                TokenBlacklistUtil.blacklist(jti,            exp);
                ActiveJwtUtil.delete   (JWT_KF.newKey(jti));
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                               .entity("JWT alvo inválido.").build();
            }
        } else {
            // revoga TODOS os JWT activos do utilizador
            ActiveJwtUtil.listActiveForUser(req.targetUsername).forEach(ent -> {
                String jti = ent.getKey().getName();
                long   exp = ent.getLong("exp");
                TokenBlacklistUtil.blacklist(jti, exp);
                ActiveJwtUtil.delete(ent.getKey());
            });
        }

        return Response.ok("{\"message\":\"Sessões terminadas com sucesso.\"}").build();
    }
}
