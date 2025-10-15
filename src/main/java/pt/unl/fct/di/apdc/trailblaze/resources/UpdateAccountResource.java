package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import com.google.cloud.datastore.*;
import com.google.cloud.datastore.Value.*;

import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;
import pt.unl.fct.di.apdc.trailblaze.util.UpdateRequest;

import java.util.*;
import java.util.stream.Collectors;

@Path("/account")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UpdateAccountResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    /* ------------------------------------------------------------ *
     *                       PUT /account/update                     *
     * ------------------------------------------------------------ */
    @PUT @Path("/update")
    public Response updateAccount(UpdateRequest req,
                                  @HeaderParam("Authorization") String hdr) {

        String jwt = token(hdr);
        if (jwt == null) return unauthorized("Token ausente.");

        try {
            String username = JwtUtil.validateToken(jwt).getBody().getSubject();
            if (username == null || username.isBlank())
                return unauthorized("Token inválido.");

            Key     k   = DS.newKeyFactory().setKind("Account").newKey(username);
            Entity  acc = DS.get(k);
            if (acc == null) return Response.status(Response.Status.NOT_FOUND)
                                            .entity("Conta não encontrada.").build();

            Entity upd = Entity.newBuilder(acc)
                    .set("name",             coalesce(req.fullName,         acc, "name"))
                    .set("address",          coalesce(req.address,          acc, "address"))
                    .set("phone",            coalesce(req.phone,            acc, "phone"))
                    .set("nationality",      coalesce(req.nationality,      acc, "nationality"))
                    .set("residenceCountry", coalesce(req.residenceCountry, acc, "residenceCountry"))
                    .set("nif",              coalesce(req.nif,              acc, "nif"))
                    .set("cc",               coalesce(req.cc,               acc, "cc"))
                    .set("profile", (req.isPublic != null && req.isPublic) ? "PUBLICO" : "PRIVADO")
                    .build();

            DS.put(upd);
            return Response.ok("Conta atualizada com sucesso.").build();

        } catch (Exception e) {
            return unauthorized("Token inválido ou expirado.");
        }
    }

    /* ------------------------------------------------------------ *
     *                 GET /account/details/{username}              *
     * ------------------------------------------------------------ */
    @GET @Path("/details/{targetUsername}")
    public Response getUserDetails(@HeaderParam("Authorization") String hdr,
                                   @PathParam("targetUsername") String targetUsr) {

        String jwt = token(hdr);
        if (jwt == null) return unauthorized("Token ausente.");

        try {
            var   claims       = JwtUtil.validateToken(jwt).getBody();
            String callerUsr   = claims.getSubject();

            /* -------- lista de roles do chamador -------- */
            @SuppressWarnings("unchecked")
            List<String> callerRoles = claims.containsKey("roles")
                    ? (List<String>) claims.get("roles", List.class)
                    : List.of(claims.get("role", String.class));   // fallback p/ JWT antigo

            Entity target = getAccount(targetUsr);
            if (target == null)
                return Response.status(Response.Status.NOT_FOUND).entity("Conta não encontrada.").build();

            /* ————————— PERMISSÕES ————————— */
            boolean isSelf      = callerUsr.equals(targetUsr);
            boolean isSys       = callerRoles.contains("SYSADMIN") || callerRoles.contains("SYSBO");

            if (!isSelf && !isSys) {
                // acesso entre utilizadores normais: mesmo role + perfil PUBLICO
                List<String> targetRoles = rolesFromAccount(target);
                String       targetProf  = target.getString("profile");
                boolean sameRoleAndPub = !Collections.disjoint(callerRoles, targetRoles)
                                          && "PUBLICO".equals(targetProf);
                if (!sameRoleAndPub)
                    return Response.status(Response.Status.FORBIDDEN)
                                   .entity("Sem permissões para ver os detalhes desta conta.").build();
            }

            /* OK – devolver info */
            return Response.ok(entityToMap(target)).build();

        } catch (Exception e) {
            return unauthorized("Token inválido ou expirado.");
        }
    }

    /* ============================================================ *
     *                              HELPERS                         *
     * ============================================================ */
    private static String token(String hdr) {
        if (hdr == null || hdr.isBlank()) return null;
        return hdr.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private static Response unauthorized(String msg) {
        return Response.status(Response.Status.UNAUTHORIZED).entity(msg).build();
    }

    /** devolve texto nulo/novo ou valor antigo da entidade  */
    private static <T> Value<?> coalesce(T newVal, Entity e, String prop) {
        if (newVal != null) {
            if (newVal instanceof String s)  return StringValue.of(s);
            if (newVal instanceof Boolean b) return BooleanValue.of(b);
            if (newVal instanceof Long l)    return LongValue.of(l);
        }
        return e.contains(prop) ? e.getValue(prop) : NullValue.of();
    }

    private static Entity getAccount(String usr) {
        if (usr == null || usr.isBlank()) return null;
        Key k = DS.newKeyFactory().setKind("Account").newKey(usr);
        return DS.get(k);
    }

    private static List<String> rolesFromAccount(Entity acc) {
        if (acc.contains("roles"))
            return acc.getList("roles").stream()
                      .map(v -> ((StringValue) v).get())
                      .collect(Collectors.toList());
        return List.of(acc.getString("role"));   // contas antigas
    }

    /** Converte a Entity para Map (simples json-friendly) */
    private static Map<String,Object> entityToMap(Entity e) {
        Map<String,Object> m = new HashMap<>();
        e.getNames().forEach(n -> {
            Value<?> v = e.getValue(n);
            if (v instanceof StringValue  sv) m.put(n, sv.get());
            else if (v instanceof LongValue lv) m.put(n, lv.get());
            else if (v instanceof BooleanValue bv) m.put(n, bv.get());
        });
        m.put("username", e.getKey().getName());
        return m;
    }
}
