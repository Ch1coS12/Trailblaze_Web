package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.cloud.datastore.*;

import java.util.List;
import java.util.stream.Collectors;

import pt.unl.fct.di.apdc.trailblaze.util.*;

@Path("/account")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RemoveAccountResource {

    /* ==================================================================== *
     *  Dependências                                                         *
     * ==================================================================== */
    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory ACC_KF = DS.newKeyFactory().setKind("Account");

    /* ==================================================================== *
     *  Utilidades                                                           *
     * ==================================================================== */
    private static String token(String hdr) {
        if (hdr == null || hdr.isBlank()) return null;
        return hdr.startsWith("Bearer ") ? hdr.substring(7).trim() : hdr.trim();
    }

    /** Devolve a lista de roles do JWT; mantém suporte a tokens antigos. */
    @SuppressWarnings("unchecked")
    private static List<String> rolesFromToken(String jwt) {
        var body = JwtUtil.validateToken(jwt).getBody();
        var list = (List<String>) body.get("roles", List.class);
        return (list != null && !list.isEmpty())
               ? list
               : List.of(body.get("role", String.class));         // fallback
    }

    private static Entity getAccount(String username) {
        return username == null ? null : DS.get(ACC_KF.newKey(username));
    }

    private static List<String> rolesFromAccount(Entity acc) {
        return acc.contains("roles")
                ? acc.getList("roles").stream()
                     .map(v -> ((StringValue) v).get())
                     .collect(Collectors.toList())
                : List.of(acc.getString("role"));
    }

    /* ==================================================================== *
     *  GC-015  →  Estado da conta                                           *
     * ==================================================================== */
    @GET @Path("/state/{username}")
    public Response getState(@HeaderParam("Authorization") String hdr,
                             @PathParam("username") String target) {

        String jwt = token(hdr);
        if (jwt == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        List<String> callerRoles;
        String       callerUsr;
        try {
            var body = JwtUtil.validateToken(jwt).getBody();
            callerUsr   = body.getSubject();
            callerRoles = rolesFromToken(jwt);
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Token inválido.").build();
        }

        /* == permissões == */
        boolean sys = callerRoles.contains("SYSADMIN") || callerRoles.contains("SYSBO");
        if (!sys && !callerUsr.equals(target))
            return Response.status(Response.Status.FORBIDDEN).entity("Sem permissões.").build();

        Entity acc = getAccount(target);
        if (acc == null)
            return Response.status(Response.Status.NOT_FOUND).entity("Conta não encontrada.").build();

        return Response.ok(
                "{\"username\":\""+target+"\",\"estado\":\""+acc.getString("state")+"\"}")
                .build();
    }

    /* ==================================================================== *
     *  GC-016  →  Perfil da conta                                           *
     * ==================================================================== */
    @GET @Path("/profile/{username}")
    public Response getProfile(@HeaderParam("Authorization") String hdr,
                               @PathParam("username") String target) {

        String jwt = token(hdr);
        if (jwt == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        List<String> callerRoles;
        String       callerUsr;
        try {
            var body = JwtUtil.validateToken(jwt).getBody();
            callerUsr   = body.getSubject();
            callerRoles = rolesFromToken(jwt);
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Token inválido.").build();
        }

        boolean sys = callerRoles.contains("SYSADMIN") || callerRoles.contains("SYSBO");
        if (!sys && !callerUsr.equals(target))
            return Response.status(Response.Status.FORBIDDEN).entity("Sem permissões.").build();

        Entity acc = getAccount(target);
        if (acc == null)
            return Response.status(Response.Status.NOT_FOUND).entity("Conta não encontrada.").build();

        return Response.ok(
                "{\"username\":\""+target+"\",\"profile\":\""+acc.getString("profile")+"\"}")
                .build();
    }

    @POST @Path("/remove")
    public Response remove(AccountStateChangeRequest req,
                           @HeaderParam("Authorization") String hdr) {

        /* ---------- autenticação ---------- */
        String jwt = token(hdr);
        if (jwt == null)
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token ausente.").build();

        List<String> callerRoles;
        try { callerRoles = rolesFromToken(jwt); }
        catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token inválido.").build();
        }

        boolean isSysAdmin = callerRoles.contains("SYSADMIN");
        boolean isSysBo    = callerRoles.contains("SYSBO");
        if (!isSysAdmin && !isSysBo)
            return Response.status(Response.Status.FORBIDDEN).entity("Sem permissões.").build();

        /* ---------- conta-alvo ---------- */
        Entity target = getAccount(req.targetUsername);
        if (target == null)
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Conta não encontrada.").build();

        List<String> targetRoles = rolesFromAccount(target);

        /* ---------- restrições de hierarquia ---------- */
        if (isSysAdmin) {
            if (targetRoles.contains("SYSADMIN"))
                return Response.status(Response.Status.FORBIDDEN)
                               .entity("SYSADMIN não pode remover outro SYSADMIN.").build();
            // SYSADMIN pode remover tudo o resto, mesmo sem estado A_REMOVER
        } else {                    // é SYSBO (porque já excluímos outros casos)
            if (targetRoles.contains("SYSADMIN") || targetRoles.contains("SYSBO"))
                return Response.status(Response.Status.FORBIDDEN)
                               .entity("SYSBO não pode remover conta SYSADMIN nem SYSBO.").build();
            // SYSBO pode remover restante, mesmo sem A_REMOVER
        }

        /* ---------- remoção efetiva ---------- */
        DS.delete(target.getKey());

        // limpar sessões ad-hoc associadas
        Query<Entity> sessQ = Query.newEntityQueryBuilder()
                .setKind("Session")
                .setFilter(StructuredQuery.PropertyFilter
                           .eq("username", req.targetUsername))
                .build();

        var results = DS.run(sessQ);
        java.util.List<Key> keys = new java.util.ArrayList<>();
        while (results.hasNext()) keys.add(results.next().getKey());
        if (!keys.isEmpty()) DS.delete(keys.toArray(Key[]::new));

        return Response.ok("{\"message\":\"Conta removida com sucesso.\","
                         + "\"sessionsRemoved\":" + keys.size() + '}')
                       .build();
    }


}