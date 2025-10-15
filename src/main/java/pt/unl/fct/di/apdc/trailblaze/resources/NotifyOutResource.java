package pt.unl.fct.di.apdc.trailblaze.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.datastore.*;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import pt.unl.fct.di.apdc.trailblaze.util.JwtUtil;

import java.util.List;

@Path("/notify-out")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NotifyOutResource {

    /* --------------------------- DAO --------------------------- */
    private static final Datastore     DS     = DatastoreOptions.getDefaultInstance().getService();
    private static final ObjectMapper  MAPPER = new ObjectMapper();

    /* --------------------------- DTO --------------------------- */
    public record TrackPosition(double latitude, double longitude) {}

    @POST
    @Path("/track-auto")
    public Response autoTrack(@HeaderParam("Authorization") String auth,
                              TrackPosition pos) {

        String token = extractToken(auth);
        if (token == null)
            return Response.status(Response.Status.UNAUTHORIZED).build();

        String operatorId;
        try {
            operatorId = JwtUtil.validateToken(token).getBody().getSubject();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Token inválido.").build();
        }

        // Procura atividades do operador ainda em curso
        Query<Entity> q = Query.newEntityQueryBuilder()
                .setKind("Activity")
                .setFilter(StructuredQuery.CompositeFilter.and(
                        StructuredQuery.PropertyFilter.eq("operatorId", operatorId),
                        StructuredQuery.PropertyFilter.eq("endTime", 0L)))
                .build();

        QueryResults<Entity> results = DS.run(q);
        boolean outside = false;

        while (results.hasNext()) {
            Entity act = results.next();
            Entity parcel = DS.get(act.getKey().getParent());
            if (parcel == null || !parcel.contains("geometry")) continue;

            try {
                var geom = MAPPER.readTree(parcel.getString("geometry"));
                if (!isInsidePolygon(geom, pos.latitude(), pos.longitude())) {
                    outside = true;
                    break;
                }
            } catch (Exception e) {
                return Response.serverError()
                               .entity("Erro na geometria da parcela: " + e.getMessage())
                               .build();
            }
        }

        return Response.ok(outside ? "FORA DA PARCELA" : "DENTRO DAS PARCELAS").build();
    }


    @GET
    @Path("/notifications")
    public Response getNotifications(@HeaderParam("Authorization") String auth) {

        String token = extractToken(auth);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String username;
        List<String> userRoles;
        try {
            var claims = JwtUtil.validateToken(token).getBody();
            username = claims.getSubject();
            userRoles = claims.containsKey("roles") 
                ? (List<String>) claims.get("roles", List.class)
                : List.of(claims.get("role", String.class));
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Token inválido.").build();
        }

        // Verificar se o utilizador tem role PRBO (apenas PRBOs recebem notificações)
        if (!userRoles.contains("PRBO")) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Apenas utilizadores PRBO podem ver notificações.").build();
        }
        Query<Entity> q = Query.newEntityQueryBuilder()
                .setKind("Notification")
                .setFilter(StructuredQuery.PropertyFilter.eq("userId", username))
                .setOrderBy(StructuredQuery.OrderBy.desc("timestamp"))
                .build();

        QueryResults<Entity> results = DS.run(q);
        ArrayNode arr = MAPPER.createArrayNode();

        while (results.hasNext()) {
            Entity e = results.next();
            ObjectNode n = arr.addObject();

            String id = e.getKey().hasName()
                        ? e.getKey().getName()
                        : String.valueOf(e.getKey().getId());

            n.put("id", id);
            n.put("userId", e.getString("userId"));
            n.put("title", e.getString("title"));
            n.put("message", e.getString("message"));
            n.put("timestamp", e.getLong("timestamp"));
            n.put("read", e.getBoolean("read"));
        }

        return Response.ok(arr).build();
    }


    /* =========================================================== */
    /*                      Helpers                                */
    /* =========================================================== */
   
    private String extractToken(String header) {
        if (header == null || header.isBlank()) return null;
        return header.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    /**
     * Teste de ponto-no-polígono (Ray Casting) para GeoJSON.
     * GeoJSON usa ordem [lon, lat]; aqui tratamos (lon, lat) = (x, y).
     */
    private boolean isInsidePolygon(com.fasterxml.jackson.databind.JsonNode geoJson,
                                    double lat, double lon) {

        var ring = geoJson.path("coordinates").get(0);   // exterior ring
        int n = ring.size();
        boolean inside = false;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double lonI = ring.get(i).get(0).asDouble(); // x_i
            double latI = ring.get(i).get(1).asDouble(); // y_i
            double lonJ = ring.get(j).get(0).asDouble(); // x_j
            double latJ = ring.get(j).get(1).asDouble(); // y_j

            boolean intersect = ((latI > lat) != (latJ > lat)) &&
                                (lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI);

            if (intersect) inside = !inside;
        }
        return inside;
    }
}