package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.cloud.datastore.*;
import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;
import pt.unl.fct.di.apdc.trailblaze.util.TokenBlacklistUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lista contas, sessões, etc. – já compatível com o novo modelo de múltiplos roles.
 */
@Path("/list")
@Produces(MediaType.APPLICATION_JSON)
public class AccountListingResource {

    private final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    /* ------------------------------------------------------------- */
    /*  Helpers                                                      */
    /* ------------------------------------------------------------- */

    /**
     * Devolve todos os roles atribuídos a uma entidade Account.
     * Compatível com o legado (campo "role" singular).
     */
    private List<String> getRoles(Entity e) {
        if (e == null) return List.of();
        if (e.contains("roles")) {
            List<Value<String>> roleValues = e.getList("roles");
            return roleValues.stream().map(Value::get).collect(Collectors.toList());
        }
        // Compatibilidade com contas antigas
        if (e.contains("role")) {
            return List.of(e.getString("role"));
        }
        return List.of();
    }

    /** SysAdmin ou SysBO. */
    private boolean isSysRole(List<String> roles) {
        return roles.contains("SYSBO") || roles.contains("SYSADMIN");
    }

    /** Primeiro role da lista ou "RU" se lista vazia. */
    private String primaryRoleOf(List<String> roles) {
        return roles.isEmpty() ? "RU" : roles.get(0);
    }

    /* ------------------------------------------------------------- */
    /*  End‑points                                                   */
    /* ------------------------------------------------------------- */

    @GET
    @Path("/all")
    public Response listAllAccounts(@HeaderParam("Authorization") String authHeader) {
        String token     = extractToken(authHeader);
        String username  = JwtUtil.getUsername(token);
        Entity requester = getUser(username);
        if (requester == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        List<String> requesterRoles = getRoles(requester);
        if (requesterRoles.isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Conta sem roles atribuídos. Contacte o administrador.")
                           .build();
        }

        StructuredQuery<Entity> query;

        if (requesterRoles.contains("RU")) {
            // RU vê apenas outros RU públicos e activados
            query = Query.newEntityQueryBuilder()
                    .setKind("Account")
                    .setFilter(StructuredQuery.CompositeFilter.and(
                            StructuredQuery.PropertyFilter.eq("roles", "RU"),
                            StructuredQuery.PropertyFilter.eq("state", "ATIVADO"),
                            StructuredQuery.PropertyFilter.eq("profile", "PUBLICO")
                    ))
                    .build();

        } else if (isSysRole(requesterRoles)) {
            // SYSADMIN ou SYSBO vê tudo
            query = Query.newEntityQueryBuilder().setKind("Account").build();

        } else {
            // Outros institucionais vêem utilizadores que partilham o primeiro role
            String principalRole = primaryRoleOf(requesterRoles);
            query = Query.newEntityQueryBuilder()
                    .setKind("Account")
                    .setFilter(StructuredQuery.PropertyFilter.eq("roles", principalRole))
                    .build();
        }

        return Response.ok(fetchResults(query)).build();
    }

    /* ---------------------- Estado ---------------------- */
    @GET
    @Path("/state/{estado}")
    public Response listByState(@HeaderParam("Authorization") String authHeader,
                                @PathParam("estado") String estado) {
        String token    = extractToken(authHeader);
        String username = JwtUtil.getUsername(token);
        Entity requester = getUser(username);
        if (requester == null || !isSysRole(getRoles(requester)))
            return Response.status(Response.Status.FORBIDDEN).build();

        StructuredQuery<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Account")
                .setFilter(StructuredQuery.PropertyFilter.eq("state", estado))
                .build();

        return Response.ok(fetchResults(query)).build();
    }

    /* ---------------------- Perfil ---------------------- */
    @GET
    @Path("/profile/{perfil}")
    public Response listByProfile(@HeaderParam("Authorization") String authHeader,
                                  @PathParam("perfil") String perfil) {
        String token    = extractToken(authHeader);
        String username = JwtUtil.getUsername(token);
        Entity requester = getUser(username);
        if (requester == null || !isSysRole(getRoles(requester)))
            return Response.status(Response.Status.FORBIDDEN).build();

        StructuredQuery<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Account")
                .setFilter(StructuredQuery.PropertyFilter.eq("profile", perfil))
                .build();

        return Response.ok(fetchResults(query)).build();
    }

    /* ---------------------- Utilizadores com sessão ---------------------- */
    @GET
    @Path("/logged")
    public Response listLoggedUsers(@HeaderParam("Authorization") String authHeader) {
        String token    = extractToken(authHeader);
        String username = JwtUtil.getUsername(token);

        Entity requester = getUser(username);
        if (requester == null)
            return Response.status(Response.Status.UNAUTHORIZED).build();

        List<String> requesterRoles = getRoles(requester);
        if (requesterRoles.isEmpty())
            return Response.status(Response.Status.FORBIDDEN).entity("Conta sem roles.").build();

        boolean isSys = isSysRole(requesterRoles);
        List<String> loggedUsers = new ArrayList<>();

        if (isSys) {
            /* SYS: vê todas as sessões + JWT ainda válidos */
            Query<Entity> sessionQ = Query.newEntityQueryBuilder().setKind("Session").build();
            datastore.run(sessionQ).forEachRemaining(s -> addIfNotPresent(loggedUsers, s.getString("username")));

            Query<Entity> jwtQ = Query.newEntityQueryBuilder().setKind("JwtActive").build();
            datastore.run(jwtQ).forEachRemaining(jwt -> processJwt(loggedUsers, jwt));
        } else {
            /* Outros institucionais: apenas utilizadores do mesmo role principal e que sejam públicos */
            String principalRole = primaryRoleOf(requesterRoles);
            Query<Entity> accountQ = Query.newEntityQueryBuilder()
                    .setKind("Account")
                    .setFilter(StructuredQuery.CompositeFilter.and(
                            StructuredQuery.PropertyFilter.eq("roles", principalRole),
                            StructuredQuery.PropertyFilter.eq("profile", "PUBLICO")
                    )).build();

            for (String allowedUser : fetchResults(accountQ)) {
                // Sessão directa
                Query<Entity> sessQ = Query.newEntityQueryBuilder()
                        .setKind("Session")
                        .setFilter(StructuredQuery.PropertyFilter.eq("username", allowedUser))
                        .build();
                if (datastore.run(sessQ).hasNext()) addIfNotPresent(loggedUsers, allowedUser);

                // JWT activos
                Query<Entity> jwtQ = Query.newEntityQueryBuilder()
                        .setKind("JwtActive")
                        .setFilter(StructuredQuery.PropertyFilter.eq("username", allowedUser))
                        .build();
                datastore.run(jwtQ).forEachRemaining(jwt -> processJwt(loggedUsers, jwt));
            }
        }

        return Response.ok(loggedUsers).build();
    }

    /* ---------------------- Por role target ---------------------- */
    @GET
    @Path("/role/{role}")
    public Response listByRole(@HeaderParam("Authorization") String authHeader,
                               @PathParam("role") String targetRole) {
        String token    = extractToken(authHeader);
        String username = JwtUtil.getUsername(token);
        Entity requester = getUser(username);
        if (requester == null || !isSysRole(getRoles(requester)))
            return Response.status(Response.Status.FORBIDDEN).build();

        StructuredQuery<Entity> query = Query.newEntityQueryBuilder()
                .setKind("Account")
                .setFilter(StructuredQuery.PropertyFilter.eq("roles", targetRole))
                .build();

        return Response.ok(fetchResults(query)).build();
    }

    /* ------------------------------------------------------------- */
    /*  Métodos utilitários internos                                 */
    /* ------------------------------------------------------------- */

    private Entity getUser(String username) {
        if (username == null || username.isBlank()) return null;
        Key key = datastore.newKeyFactory().setKind("Account").newKey(username);
        return datastore.get(key);
    }

    private List<String> fetchResults(Query<Entity> query) {
        List<String> res = new ArrayList<>();
        datastore.run(query).forEachRemaining(e -> res.add(e.getKey().getName()));
        return res;
    }

    private String extractToken(String header) {
        if (header == null || header.isBlank())
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Authorization header ausente.").build());
        final String PREFIX = "Bearer ";
        return header.startsWith(PREFIX) ? header.substring(PREFIX.length()) : header;
    }

    /* ---- helpers para listLoggedUsers ---- */
    private void addIfNotPresent(List<String> list, String user) {
        if (!list.contains(user)) list.add(user);
    }

    private void processJwt(List<String> list, Entity jwt) {
        String user = jwt.getString("username");
        String jti  = jwt.getKey().getName();
        long exp    = jwt.getLong("exp");
        boolean valid = exp > System.currentTimeMillis() && !TokenBlacklistUtil.isBlacklisted(jti);
        if (valid) addIfNotPresent(list, user);
    }
}
