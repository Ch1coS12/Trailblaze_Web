package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.google.cloud.datastore.*;

import java.util.List;

import pt.unl.fct.di.apdc.trailblaze.util.*;

@Path("/fo")
@Consumes("application/geo+json")          // o corpo é o ficheiro GeoJSON
@Produces(MediaType.APPLICATION_JSON)
public class WorkSheetImportResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory WS_KF = DS.newKeyFactory().setKind("WorkSheet");

    /* =============================================================== *
     *                POST /fo/import      (SMBO)                       *
     * =============================================================== */
    @POST
    @Path("/import")
    public Response importFO(@HeaderParam("Authorization") String hdr,
                             String geoJsonBody) {

        /* ---------- 1) autenticação ---------- */
        String jwt = token(hdr);
        if (jwt == null)
            return unauthorized("JWT ausente.");

        String username;
        List<String> callerRoles;
        try {
            var claims   = JwtUtil.validateToken(jwt).getBody();
            username     = claims.getSubject();
            callerRoles  = claims.containsKey("roles")
                         ? claims.get("roles", List.class)
                         : List.of(claims.get("role", String.class));   // compat. tokens antigos
        } catch (Exception e) {
            return unauthorized("JWT inválido.");
        }

        if (!callerRoles.contains("SMBO"))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Apenas SMBO pode importar folhas de obra.").build();

        /* ---------- 2) estado da conta ---------- */
        Entity acc = DS.get(DS.newKeyFactory().setKind("Account").newKey(username));
        if (acc == null || !"ATIVADA".equals(acc.getString("state")))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Conta inativa ou inexistente.").build();

        /* ---------- 3) validação do GeoJSON ---------- */
        WorkSheetUtil.ParsedWS dto;
        try {
            dto = WorkSheetUtil.parseAndValidate(geoJsonBody);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity(ex.getMessage()).build();
        }

        /* ---------- 4) duplicação ---------- */
        Key wsKey = WS_KF.newKey(dto.id());
        if (DS.get(wsKey) != null)
            return Response.status(Response.Status.CONFLICT)
                           .entity("Folha de obra já existe.").build();

        /* ---------- 5) transação ---------- */
        Transaction txn = DS.newTransaction();
        try {
            // 5-A WorkSheet
            txn.put(WorkSheetUtil.buildWorkSheetEntity(dto, wsKey, username));

            // 5-B Operations (até 5)
            WorkSheetUtil.buildOperationEntities(dto, wsKey)
                         .forEach(txn::put);

            // 5-C Parcels
            WorkSheetUtil.buildParcelEntities(dto, wsKey)
                         .forEach(txn::put);

            txn.commit();
            return Response.status(Response.Status.CREATED)
                           .entity("{\"msg\":\"Folha de obra importada com sucesso\", \"id\":" + dto.id() + "}").build();

        } catch (Exception ex) {
            if (txn.isActive()) txn.rollback();
            ex.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Erro ao persistir: " + ex.getMessage()).build();
        }
    }

    /* ---------------- helpers ---------------- */
    private static String token(String hdr) {
        return hdr != null ? hdr.replaceFirst("(?i)^Bearer\\s+", "").trim() : null;
    }

    private static Response unauthorized(String msg) {
        return Response.status(Response.Status.UNAUTHORIZED).entity(msg).build();
    }
}
