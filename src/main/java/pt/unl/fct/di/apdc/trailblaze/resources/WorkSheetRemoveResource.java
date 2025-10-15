package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import static jakarta.ws.rs.core.Response.Status;

import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;

import java.util.ArrayList;
import java.util.List;

import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

/**
 * REM-FO – Remoção de uma folha de obra e de todas as entidades filhas.
 * • Apenas utilizadores SMBO (conta ATIVADA) podem remover.
 * • DELETE /rest/fo/{id}
 */
@Path("/fo")
@Produces(MediaType.APPLICATION_JSON)
public class WorkSheetRemoveResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory WS_KF = DS.newKeyFactory().setKind("WorkSheet");
    private static final KeyFactory ACC_KF = DS.newKeyFactory().setKind("Account");

    /* =============================================================== */
    /*                       DELETE /fo/{id}                           */
    /* =============================================================== */
    @DELETE
    @Path("/{id}")
    public Response remove(@HeaderParam("Authorization") String hdr,
                           @PathParam("id") long id) {

        /* ---------- 1) autenticação / autorização ---------- */
        String jwt = token(hdr);
        if (jwt == null) return unauthorized("JWT ausente.");

        List<String> roles;
        String user;
        try {
            var c = JwtUtil.validateToken(jwt).getBody();
            user  = c.getSubject();
            roles = c.containsKey("roles")                 // tokens novos
                    ? c.get("roles", List.class)
                    : List.of(c.get("role", String.class)); // compatibilidade tokens antigos
        } catch (Exception e) {
            return unauthorized("JWT inválido.");
        }

        if (!roles.contains("SMBO"))
            return Response.status(Status.FORBIDDEN)
                           .entity("Apenas SMBO pode remover folhas de obra.").build();

        /* ---------- 2) estado da conta ---------- */
        Entity acc = DS.get(ACC_KF.newKey(user));
        if (acc == null || !"ATIVADA".equals(acc.getString("state")))
            return Response.status(Status.FORBIDDEN)
                           .entity("Conta inativa ou inexistente.").build();

        /* ---------- 3) verificar existência ---------- */
        Key wsKey = WS_KF.newKey(id);
        if (DS.get(wsKey) == null)
            return Response.status(Status.NOT_FOUND)
                           .entity("Folha de obra não encontrada.").build();

        /* ---------- 4) remoção em transação ---------- */
        Transaction txn = DS.newTransaction();
        try {
            // 🔹 4-A  Apagar Operations associadas à worksheet
            queryKeys("Operation", wsKey).forEachRemaining(txn::delete);

            // 🔹 4-B  Apagar Parcelas associadas à worksheet
            queryKeys("Parcel", wsKey).forEachRemaining(txn::delete);

            // 🔹 4-C  Buscar ExecutionSheets associadas à worksheet
            Query<Entity> execSheetsQuery = Query.newEntityQueryBuilder()
                    .setKind("ExecutionSheet")
                    .setFilter(PropertyFilter.eq("associatedWorkSheetId", String.valueOf(id)))
                    .build();

            QueryResults<Entity> execSheets = DS.run(execSheetsQuery);

            while (execSheets.hasNext()) {
                Entity es = execSheets.next();
                String esId = es.getKey().getName(); // ou getId() dependendo de como estás a gerar IDs

                // 🔹 Buscar OperationExecutions associadas à ExecutionSheet
                Query<Entity> opExecQuery = Query.newEntityQueryBuilder()
                        .setKind("OperationExecution")
                        .setFilter(PropertyFilter.eq("executionSheetId", esId))
                        .build();

                QueryResults<Entity> opExecs = DS.run(opExecQuery);
                List<String> opExecIds = new ArrayList<>();
                List<Key> opExecKeys = new ArrayList<>();

                while (opExecs.hasNext()) {
                    Entity opExec = opExecs.next();
                    opExecIds.add(opExec.getKey().getName());
                    opExecKeys.add(opExec.getKey());
                }

                // 🔹 Buscar ParcelOperationExecutions associadas a cada OperationExecution
                List<String> parcelExecIds = new ArrayList<>();
                List<Key> parcelExecKeys = new ArrayList<>();

                for (String opId : opExecIds) {
                    Query<Entity> parcelExecQuery = Query.newEntityQueryBuilder()
                            .setKind("ParcelOperationExecution")
                            .setFilter(PropertyFilter.eq("operationExecutionId", opId))
                            .build();

                    QueryResults<Entity> parcelExecs = DS.run(parcelExecQuery);
                    while (parcelExecs.hasNext()) {
                        Entity parcelExec = parcelExecs.next();
                        parcelExecIds.add(parcelExec.getKey().getName());
                        parcelExecKeys.add(parcelExec.getKey());
                    }
                }

                // 🔹 Buscar Activities associadas a cada ParcelOperationExecution
                List<Key> activityKeys = new ArrayList<>();
                for (String parcelId : parcelExecIds) {
                    Query<Entity> activityQuery = Query.newEntityQueryBuilder()
                            .setKind("Activity")
                            .setFilter(PropertyFilter.eq("parcelOperationExecutionId", parcelId))
                            .build();

                    QueryResults<Entity> activities = DS.run(activityQuery);
                    while (activities.hasNext()) {
                        Entity act = activities.next();
                        activityKeys.add(act.getKey());
                    }
                }

                // 🔹 Apagar tudo na ordem correta
                if (!activityKeys.isEmpty()) txn.delete(activityKeys.toArray(new Key[0]));
                if (!parcelExecKeys.isEmpty()) txn.delete(parcelExecKeys.toArray(new Key[0]));
                if (!opExecKeys.isEmpty()) txn.delete(opExecKeys.toArray(new Key[0]));
                txn.delete(es.getKey()); // apagar a ExecutionSheet
            }

            // 🔹 4-D  Apagar a própria WorkSheet
            txn.delete(wsKey);

            txn.commit();
            return Response.ok("{\"msg\":\"Folha de obra removida com sucesso\"}").build();

        } catch (Exception ex) {
            if (txn.isActive()) txn.rollback();
            ex.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                           .entity("Erro ao remover: " + ex.getMessage()).build();
        }

    }

    /* ---------------- helpers ---------------- */

    /** Remove “Bearer ” (case-insensitive) e devolve null se header vazio. */
    private static String token(String hdr) {
        return hdr == null ? null : hdr.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private static Response unauthorized(String msg) {
        return Response.status(Status.UNAUTHORIZED).entity(msg).build();
    }

    /** Devolve só as chaves de um kind filho da chave‐pai fornecida. */
    private static QueryResults<Key> queryKeys(String kind, Key ancestor) {
        Query<Key> q = Query.newKeyQueryBuilder()
                            .setKind(kind)
                            .setFilter(PropertyFilter.hasAncestor(ancestor))
                            .build();
        return DS.run(q);
    }
}
