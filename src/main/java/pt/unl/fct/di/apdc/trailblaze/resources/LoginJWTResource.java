package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import com.google.cloud.datastore.*;

import pt.unl.fct.di.apdc.trailblaze.util.*;

import java.util.List;
import java.util.stream.Collectors;

@Path("/login-jwt")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LoginJWTResource {

    private static final Datastore           DS      = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory          ACC_KF  = DS.newKeyFactory().setKind("Account");

    /* =========================================================== */
    /*                       POST  /login-jwt                      */
    /* =========================================================== */
    @POST
    public Response login(LoginRequest req) {

        /* ---------- 0. validação do payload ---------- */
        if (req == null || req.username == null || req.password == null
                       || req.username.isBlank()  || req.password.isBlank())
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("username e password obrigatórios.").build();

        /* ---------- 1. localizar conta (username ou email) ---------- */
        String actualUsername = req.username;                       // pode ser username ou email
        Entity user = DS.get(ACC_KF.newKey(actualUsername));

        if (user == null) {                                         // procurar por email
            Query<Entity> q = Query.newEntityQueryBuilder()
                                    .setKind("Account")
                                    .setFilter(StructuredQuery.PropertyFilter.eq("email", req.username))
                                    .build();
            var it = DS.run(q);
            if (!it.hasNext())
                return Response.status(Response.Status.UNAUTHORIZED)
                               .entity("Utilizador não existe.").build();

            user            = it.next();
            actualUsername  = user.getKey().getName();              // username real
        }

        /* ---------- 2. password ---------- */
        if (!HashUtil.checkPassword(req.password, user.getString("password")))
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Password incorreta.").build();

        /* ---------- 3. estado ---------- */
        if (!"ATIVADA".equals(user.getString("state")))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Conta não está ativada.").build();

        /* ---------- 4. lista de roles ---------- */
        List<String> roles;
        if (user.contains("roles")) {                               // conta já na versão nova
            roles = user.<StringValue>getList("roles")              // evita _unchecked cast_
                         .stream()
                         .map(StringValue::get)
                         .collect(Collectors.toList());
            System.out.println("User " + actualUsername + " has roles from 'roles' field: " + roles);
        } else {                                                    // conta antiga → só um role
            roles = List.of(user.getString("role"));
            System.out.println("User " + actualUsername + " has role from legacy 'role' field: " + roles);
        }

        /* ---------- 5. gerar JWT ---------- */
        String jwt = JwtUtil.generateToken(actualUsername, roles);

        /* ---------- 6. devolver ----------- */
        return Response.ok("{\"token\":\"" + jwt + "\"}").build();
    }
}