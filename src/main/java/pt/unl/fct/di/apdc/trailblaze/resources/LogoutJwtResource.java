package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import pt.unl.fct.di.apdc.trailblaze.util.*;
import com.google.cloud.datastore.*;

@Path("/logout/jwt")
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.APPLICATION_JSON)
public class LogoutJwtResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory KF = DS.newKeyFactory().setKind("JwtActive");

    /* ==================================================== */
    /*                   POST  /logout/jwt                  */
    /* ==================================================== */
    @POST
    public Response logout(@HeaderParam("Authorization") String authHdr) {

        /* ---------- 1. header presente? ---------- */
        if (authHdr == null || authHdr.isBlank())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Authorization header ausente.").build();

        /* ---------- 2. extrair token (case-insensitive) ---------- */
        String token = authHdr.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isEmpty())
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token mal formatado.").build();

        try {
            /* ---------- 3. valida & coloca na blacklist ---------- */
            var claims = JwtUtil.validateToken(token).getBody();

            String jti = claims.getId();                 // unique-id do JWT
            long   exp = claims.getExpiration().getTime();

            TokenBlacklistUtil.blacklist(jti, exp);      // marca como revogado
            ActiveJwtUtil.delete(KF.newKey(jti));        // remove dos ativos

            return Response.ok("{\"message\":\"Logout efetuado com sucesso.\"}")
                           .build();

        } catch (Exception e) {                          // inclui expirado / assinatura falhou
            return Response.status(Response.Status.UNAUTHORIZED)
                           .entity("Token inválido ou expirado.").build();
        }
    }
}
