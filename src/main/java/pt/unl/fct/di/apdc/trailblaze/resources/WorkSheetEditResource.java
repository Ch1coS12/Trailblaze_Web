package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import pt.unl.fct.di.apdc.trailblaze.util.EditWorkSheetRequest;
import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

@Path("/fo")
@Produces(MediaType.APPLICATION_JSON)
public class WorkSheetEditResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory WS_KF = DS.newKeyFactory().setKind("WorkSheet");

    /* ============================================================= *
     *                    PUT /fo/{id}    (SMBO)                      *
     * ============================================================= */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response edit(@HeaderParam("Authorization") String hdr,
                         @PathParam("id") long id,
                         EditWorkSheetRequest req) {

        /* ---------- 1) autenticação ---------- */
        String jwt = token(hdr);
        if (jwt == null)
            return unauthorized("JWT ausente.");

        List<String> callerRoles;
        String callerUsr;
        try {
            var claims = JwtUtil.validateToken(jwt).getBody();
            callerUsr   = claims.getSubject();
            callerRoles = claims.containsKey("roles")
                       ? claims.get("roles", List.class)
                       : List.of(claims.get("role", String.class));      // compat.
        } catch (Exception e) {
            return unauthorized("JWT inválido.");
        }

        if (!callerRoles.contains("SMBO"))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Apenas SMBO pode editar folhas de obra.").build();

        /* ---------- 2) conta do utilizador ---------- */
        Entity acc = DS.get(DS.newKeyFactory().setKind("Account").newKey(callerUsr));
        if (acc == null || !"ATIVADA".equals(acc.getString("state")))
            return Response.status(Response.Status.FORBIDDEN)
                           .entity("Conta inativa ou inexistente.").build();

        /* ---------- 3) folha de obra ---------- */
        Entity ws = DS.get(WS_KF.newKey(id));
        if (ws == null)
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Folha de obra não encontrada.").build();

        /* ---------- 4) construir nova entidade ---------- */
        Entity.Builder b = Entity.newBuilder(ws);
        try {
            // datas (yyyy-MM-dd)
            if (req.startingDate  != null) b.set("startingDate",  toTs(req.startingDate));
            if (req.finishingDate != null) b.set("finishingDate", toTs(req.finishingDate));
            if (req.issueDate     != null) b.set("issueDate",     toTs(req.issueDate));
            if (req.awardDate     != null) b.set("awardDate",     toTs(req.awardDate));

            // restantes campos
            if (req.serviceProviderId != null) b.set("serviceProviderId", req.serviceProviderId);
            if (req.posaCode          != null) b.set("posaCode",          req.posaCode);
            if (req.posaDescription   != null) b.set("posaDescription",   req.posaDescription);
            if (req.pospCode          != null) b.set("pospCode",          req.pospCode);
            if (req.pospDescription   != null) b.set("pospDescription",   req.pospDescription);

        } catch (DateTimeParseException ex) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("Formato de data inválido (use yyyy-MM-dd).").build();
        }

        /* ---------- 5) transação ---------- */
        Transaction txn = DS.newTransaction();
        try {
            txn.put(b.build());

            // eliminação opcional de parcelas
            if (req.removePolygonIds != null && !req.removePolygonIds.isEmpty()) {
                KeyFactory pkf = DS.newKeyFactory()
                                   .addAncestor(PathElement.of("WorkSheet", id))
                                   .setKind("Parcel");
                req.removePolygonIds.forEach(pid -> txn.delete(pkf.newKey(pid)));
            }

            txn.commit();
            return Response.ok("{\"msg\":\"Folha de obra editada com sucesso.\"}").build();

        } catch (Exception ex) {
            if (txn.isActive()) txn.rollback();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Erro ao editar: " + ex.getMessage()).build();
        }
    }

    /* ================= helpers ================= */
    private static String token(String hdr) {
        return hdr != null ? hdr.replaceFirst("(?i)^Bearer\\s+", "").trim() : null;
    }

    private static Response unauthorized(String msg) {
        return Response.status(Response.Status.UNAUTHORIZED).entity(msg).build();
    }

    private static Timestamp toTs(String iso) {
        return Timestamp.parseTimestamp(LocalDate.parse(iso) + "T00:00:00Z");
    }
}
