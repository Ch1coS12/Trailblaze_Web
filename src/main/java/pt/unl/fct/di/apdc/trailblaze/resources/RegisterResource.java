package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.cloud.datastore.*;

import java.util.*;
import java.util.regex.Pattern;

import pt.unl.fct.di.apdc.trailblaze.util.*;

@Path("/register")
@Produces(MediaType.APPLICATION_JSON)
public class RegisterResource {

    /* ------------------------------------------------------------------ *
     *  • Dependências e constantes                                        *
     * ------------------------------------------------------------------ */
    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();

    private static final Set<String> INSTITUTIONAL_ROLES = Set.of(
            "SYSADMIN","SYSBO","SMBO","SGVBO","SDVBO","PRBO","PO","ADLU");

    private static final Set<String> VALID_ROLES;
    static {
        VALID_ROLES = new HashSet<>(INSTITUTIONAL_ROLES);
        VALID_ROLES.add("RU");
    }

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Pattern STRONG_PASSWORD_RE =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,}$");

    /* ------------------------------------------------------------------ *
     *  • Registo de utilizador “cívico” (sempre RU)                       *
     * ------------------------------------------------------------------ */
    @POST @Path("/civic")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerCivic(CivicRegisterRequest req) {
        req.setRole("RU");                           
        return internalRegister(req, null);          
    }

    /* ------------------------------------------------------------------ *
     *  • Registo de utilizador institucional                              *
     * ------------------------------------------------------------------ */
    @POST @Path("/institutional")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerInstitutional(@HeaderParam("Authorization") String header,
                                          InstitutionalBaseRequest req) {

       
        String token = extractToken(header);
        if (token == null)
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("JWT ausente.").build();

        
        List<String> callerRoles;
        String       callerUsr;
        try {
            var body = JwtUtil.validateToken(token).getBody();
            callerUsr   = body.getSubject();

            @SuppressWarnings("unchecked")
            List<String> tmp = (List<String>) body.get("roles", List.class);
            callerRoles = (tmp != null && !tmp.isEmpty())
                          ? tmp
                          : List.of(body.get("role", String.class));    

        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token inválido.").build();
        }

        if (!callerRoles.contains("SYSADMIN") && !callerRoles.contains("SYSBO"))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Apenas SYSADMIN/SYSBO podem criar contas institucionais.")
                           .build();

       
        if (!INSTITUTIONAL_ROLES.contains(req.getRole()))
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Role não permitido neste endpoint.")
                           .build();

    
        return internalRegister(req, callerUsr);
    }

    /* ================================================================== *
     *  MÉTODO PRIVADO – lógica comum de criação da conta                 *
     * ================================================================== */
    private Response internalRegister(RegisterRequest req, String creatorUsr) {

        try {
            /* --- validações genéricas --- */
            if (req == null)
                return bad("Empty body.");

            if (blank(req.getUsername()) || blank(req.getPassword()) ||
                blank(req.getEmail())    || blank(req.getFullName()))
                return bad("Missing fileds.");

            if (!EMAIL_RE.matcher(req.getEmail()).matches())
                return bad("Invalid email.");

            if (!VALID_ROLES.contains(req.getRole()))
                return bad("Unknown role.");

            if (!STRONG_PASSWORD_RE.matcher(req.getPassword()).matches())
                return bad("Password fraca. Deve ter letras maiúsculas, minúsculas, números e sinais de pontuação, com pelo menos 8 caracteres.");

            /* --- unicidade do username --- */
            Key accKey = DS.newKeyFactory().setKind("Account").newKey(req.getUsername());
            if (DS.get(accKey) != null)
                return Response.status(Response.Status.CONFLICT)
                               .entity("Username já existe.").build();

            /* --- lista de roles atribuídos à conta --- */
            Set<String> roles = new LinkedHashSet<>();
            roles.add(req.getRole());

            /* regra de negócio antiga mantida */
            switch (req.getRole()) {
            case "SYSADMIN","SYSBO","SMBO","PRBO","PO","ADLU" -> {
                roles.add("SGVBO");
                roles.add("SDVBO");
            }
            case "RU" -> {
                roles.add("SGVBO");
                roles.add("SDVBO");   // <-- adiciona também SDVBO
            }
        }
            

            /* converte roles → ListValue */
            ListValue rolesVal = ListValue.of(
                    roles.stream().map(StringValue::of).toList());

            boolean isInstitutional = INSTITUTIONAL_ROLES.contains(req.getRole());
            String initialState     = isInstitutional ? "INATIVO" : "ATIVADA";

            String  responseMsg     = "Account registered.";
            
            Entity.Builder b = Entity.newBuilder(accKey)
                    .set("email",    req.getEmail())
                    .set("password", HashUtil.hashPassword(req.getPassword()))
                    .set("name",     req.getFullName())

                    .set("roles",    rolesVal)
                    .set("state", initialState)    

                    .set("profile",  req.getIsPublic() ? "PUBLICO" : "PRIVADO")
                    .set("registrationType",
                         isInstitutional ? "INSTITUTIONAL" : "CIVIC");

            if (creatorUsr != null)
                b.set("creator", creatorUsr);

            /* campos específicos de subclasses */
            if (req instanceof InstitutionalBaseRequest inst)
                inst.applyToBuilder(b);
            else if (req instanceof CivicRegisterRequest civ)
                civ.applyToBuilder(b);

            DS.put(b.build());

            return Response.status(Response.Status.CREATED)
                    .entity(responseMsg)             
                    .build();

        } catch (Exception ex) {
            ex.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Erro no registo: " + ex.getMessage())
                           .build();
        }
    }

    
    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static Response bad(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
    }

    private static String extractToken(String header) {
        if (header == null || header.isBlank()) return null;
        return header.startsWith("Bearer ") ? header.substring(7).trim()
                                            : header.trim();
    }
}