package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.cloud.datastore.*;

import io.jsonwebtoken.Claims;
import pt.unl.fct.di.apdc.trailblaze.util.AccountStateChangeRequest;
import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

/**
 * POST /rest/activate
 *
 * Body : { "targetUsername" : "<utilizador a ativar>" }
 * Header Authorization : "Bearer <token-jwt>"
 */
@Path("/activate")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class ActivateResource {

    private static final Datastore DS =
            DatastoreOptions.getDefaultInstance().getService();

    /* ----------------------------------------------------- */
    /* ----------------------  END-POINT  ------------------- */
    /* ----------------------------------------------------- */
    @POST
    public Response activateAccount(AccountStateChangeRequest req,
                                    @HeaderParam("Authorization") String auth) {

        /* -------- token presente? -------- */
        if (auth == null || auth.isBlank())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token ausente.").build();

        String token = auth.replaceFirst("(?i)^Bearer\\s+", "").trim();

        try {
            /* -------- validar JWT -------- */
            Claims claims = JwtUtil.validateToken(token).getBody();
            String requesterUsername = claims.getSubject();
            List<String> requesterRoles = rolesFromClaims(claims);

            /* -------- ver se pode ativar -------- */
            if (!requesterRoles.contains("SYSADMIN") &&
                !requesterRoles.contains("SYSBO"))
                return Response.status(Response.Status.FORBIDDEN)
                               .entity("Sem permissões para ativar contas.")
                               .build();

            /* -------- conta-alvo -------- */
            Entity target = DS.get(DS.newKeyFactory()
                                     .setKind("Account")
                                     .newKey(req.targetUsername));

            if (target == null)
                return Response.status(Response.Status.NOT_FOUND)
                               .entity("Conta não encontrada.").build();

            List<String> targetRoles = rolesFromEntity(target);

            /* SYSBO não pode ativar SYSADMIN nem outro SYSBO */
            if (requesterRoles.contains("SYSBO") &&
                (targetRoles.contains("SYSADMIN") || targetRoles.contains("SYSBO")))
                return Response.status(Response.Status.FORBIDDEN)
                               .entity("SYSBO não pode ativar esta conta.")
                               .build();

            if ("ATIVADA".equals(target.getString("state")))
                return Response.status(Response.Status.CONFLICT)
                               .entity("Conta já está ativada.").build();

            /* -------- persistir mudança -------- */
            DS.put(Entity.newBuilder(target).set("state", "ATIVADA").build());
            return Response.ok("Conta ativada com sucesso.").build();

        } catch (Exception ex) {               // inclui token inválido/expirado
            ex.printStackTrace();
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("ERRO: " + ex.getMessage()).build();
        }
    }

    /* ----------------------------------------------------- */
    /* -----------------  HELPERS PRIVADOS  ---------------- */
    /* ----------------------------------------------------- */

    /** Extrai lista de roles do JWT (suporta ‘roles’ e campo legado ‘role’). */
    private static List<String> rolesFromClaims(Claims c) {
        Object r = c.get("roles");
        if (r instanceof List<?> list && !list.isEmpty())
            return list.stream().map(String::valueOf).collect(Collectors.toList());

        String legacy = c.get("role", String.class);
        return legacy == null ? Collections.emptyList() : List.of(legacy);
    }

    /** Extrai lista de roles de uma Entity (suporta versões antigas). */
    private static List<String> rolesFromEntity(Entity e) {
        if (e.contains("roles"))
            return e.getList("roles")
                    .stream()
                    .map(v -> ((StringValue) v).get())
                    .collect(Collectors.toList());

        if (e.contains("role"))                  // esquema antigo
            return List.of(e.getString("role"));

        return Collections.emptyList();
    }
}
