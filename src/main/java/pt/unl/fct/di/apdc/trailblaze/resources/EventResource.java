package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;

import java.util.*;
import java.util.stream.*;

import pt.unl.fct.di.apdc.trailblaze.util.*;

/**
 * CRUD de eventos ligados a folhas de obra.
 *
 * • SYSBO / SYSADMIN ⇒ cria, edita, apaga eventos e consulta inscrições
 * • RU              ⇒ inscreve-se / consulta eventos em que está inscrito
 * • Todos os utilizadores autenticados podem ver a lista completa
 */
@Path("/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {

    /* --------------------- Datastore --------------------- */
    private static final Datastore  DS       = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory KF_EVENT = DS.newKeyFactory().setKind("Event");
    private static final KeyFactory KF_REG   = DS.newKeyFactory().setKind("EventReg");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* ======================== Helpers JWT ======================== */

    /** Extrai o JWT de um header “Authorization”. Retorna {@code null} se ausente. */
    private static String jwt(String hdr) {
        return hdr != null && hdr.startsWith("Bearer ") ? hdr.substring(7) : null;
    }

    /** Converte as claims «roles» (lista) ou «role» (legado) em lista de strings. */
    private static List<String> roles(String token) {
        try {
            Claims c   = JwtUtil.validateToken(token).getBody();
            Object raw = c.get("roles");
            if (raw instanceof List<?> list && !list.isEmpty())
                return list.stream().map(String::valueOf).collect(Collectors.toList());

            String legacy = c.get("role", String.class);
            return legacy == null ? List.of() : List.of(legacy);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Username (subject) do JWT. */
    private static String username(String token) {
        return JwtUtil.getUsername(token);
    }

    
    
    /** @return {@code true} se o utilizador tiver privilégio SYSADMIN ou SYSBO. */
    private static boolean isElevated(List<String> roles) {
        return roles.contains("SYSADMIN") || roles.contains("SYSBO");
    }

    /* ------------------------ Geo utils ------------------------- */
    private static double toRadians(double deg) { return deg * Math.PI / 180.0; }

    /**
     * Distância aproximada (km) usando fórmula de Haversine.
     */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // raio médio da Terra em km
        double dLat = toRadians(lat2 - lat1);
        double dLon = toRadians(lon2 - lon1);
        double a    = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                      Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
                      Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /* ------------------------ Worksheet polygons ---------------------- */
    private static ArrayNode polygonsOf(String wsId) {
        ArrayNode arr = MAPPER.createArrayNode();
        try {
            long id = Long.parseLong(wsId);
            Key wsKey = DS.newKeyFactory().setKind("WorkSheet").newKey(id);
            Query<Entity> q = Query.newEntityQueryBuilder()
                                    .setKind("Parcel")
                                    .setFilter(PropertyFilter.hasAncestor(wsKey))
                                    .build();
            DS.run(q).forEachRemaining(p -> {
                if (p.contains("geometry")) {
                    try {
                        arr.add(MAPPER.readTree(p.getString("geometry")));
                    } catch(Exception ignored) {}
                }
            });
        } catch(Exception e) {
            // ignore invalid id
        }
        return arr;
    }

    /* ============================================================= */
    /*                GET /events  (todos os roles)                  */
    /* ============================================================= */
    @GET
    public Response listAll(@HeaderParam("Authorization") String auth,
                            @QueryParam("lat") Double lat,
                            @QueryParam("lng") Double lng) {

        String token = jwt(auth);
        if (token == null)
            return Response.status(Response.Status.UNAUTHORIZED).build();

        // Buscar todos os eventos
        Query<Entity> q = Query.newEntityQueryBuilder().setKind("Event").build();
        List<Event> events = new ArrayList<>();
        DS.run(q).forEachRemaining(e -> events.add(Event.fromEntity(e)));

        // Ordenar por proximidade se coordenadas fornecidas
        if (lat != null && lng != null) {
            events.sort(Comparator.comparingDouble(ev -> {
                try {
                    String[] parts = ev.location.split(",");
                    double eLat = Double.parseDouble(parts[0]);
                    double eLng = Double.parseDouble(parts[1]);
                    return haversine(lat, lng, eLat, eLng);
                } catch (Exception ex) {
                    return Double.MAX_VALUE; // ignora locais malformados
                }
            }));
        }
        return Response.ok(events).build();
    }
    
    
    /* ============================================================= */
    /*                GET /events/public  (sem auth)                 */
    /* ============================================================= */
    @GET @Path("/public")
    public Response listPublic(@QueryParam("lat") Double lat,
                               @QueryParam("lng") Double lng) {

        Query<Entity> q = Query.newEntityQueryBuilder().setKind("Event").build();
        List<ObjectNode> list = new ArrayList<>();
        DS.run(q).forEachRemaining(e -> {
            Event ev = Event.fromEntity(e);
            ObjectNode o = MAPPER.createObjectNode();
            o.put("id", ev.id);
            o.put("title", ev.title);
            o.put("description", ev.description);
            o.put("dateTime", ev.dateTime.getTime());
            o.put("location", ev.location);
            o.put("workSheetId", ev.workSheetId);
            o.set("polygons", polygonsOf(ev.workSheetId));
            list.add(o);
        });

        if (lat != null && lng != null) {
            list.sort(Comparator.comparingDouble(o -> {
                String loc = o.path("location").asText("");
                try {
                    String[] p = loc.split(",");
                    double eLat = Double.parseDouble(p[0]);
                    double eLng = Double.parseDouble(p[1]);
                    return haversine(lat, lng, eLat, eLng);
                } catch (Exception ex) {
                    return Double.MAX_VALUE;
                }
            }));
        }

        ArrayNode arr = MAPPER.createArrayNode();
        list.forEach(arr::add);
        return Response.ok(arr.toString()).build();
    }

    /* ============================================================= */
    /*                    POST /events  (SYSBO|SYSADMIN)             */
    /* ============================================================= */
    @POST
    public Response create(@HeaderParam("Authorization") String auth,
                           CreateEventRequest req) {

        String token = jwt(auth);
        if (token == null)
            return Response.status(Response.Status.UNAUTHORIZED).build();

        List<String> callerRoles = roles(token);
        if (!isElevated(callerRoles))
            return Response.status(Response.Status.FORBIDDEN).build();

        if (req == null || !req.isValid())
            return Response.status(Response.Status.BAD_REQUEST).entity("Pedido inválido").build();

        if (!ExecutionSheetUtil.workSheetExists(req.workSheetId))
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("Folha de obra não encontrada").build();

        Event ev = new Event();
        ev.id          = UUID.randomUUID().toString();
        ev.title       = req.title;
        ev.description = req.description;
        ev.dateTime    = new Date(req.dateTimeMillis);
        ev.location    = req.location; // formato "lat,lng"
        ev.workSheetId = req.workSheetId;
        ev.createdBy   = username(token);
        DS.put(ev.toEntity(DS));
        return Response.status(Response.Status.CREATED).entity(ev).build();
    }

    /* ============================================================= */
    /*          PUT /events/{id}  (SYSBO|SYSADMIN ou criador)        */
    /* ============================================================= */
    @PUT @Path("/{id}")
    public Response update(@HeaderParam("Authorization") String auth,
                           @PathParam("id") String id,
                           CreateEventRequest req) {

        String token = jwt(auth);
        if (token == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        Entity ent = DS.get(KF_EVENT.newKey(id));
        if (ent == null) return Response.status(Response.Status.NOT_FOUND).build();

        Event ev = Event.fromEntity(ent);
        List<String> callerRoles = roles(token);
      

        if (!isElevated(callerRoles) )
            return Response.status(Response.Status.FORBIDDEN).build();

        if (req == null || !req.isValid())
            return Response.status(Response.Status.BAD_REQUEST).entity("Pedido inválido").build();

        // Actualizar campos
        ev.title       = req.title;
        ev.description = req.description;
        ev.dateTime    = new Date(req.dateTimeMillis);
        ev.location    = req.location;
        ev.workSheetId = req.workSheetId;
        DS.put(ev.toEntity(DS));
        return Response.ok(ev).build();
    }

    /* ============================================================= */
    /*          DELETE /events/{id}  (SYSBO|SYSADMIN ou criador)     */
    /* ============================================================= */
    @DELETE @Path("/{id}")
    public Response delete(@HeaderParam("Authorization") String auth,
                           @PathParam("id") String id) {

        String token = jwt(auth);
        if (token == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        Entity ent = DS.get(KF_EVENT.newKey(id));
        if (ent == null) return Response.status(Response.Status.NOT_FOUND).build();

        Event ev = Event.fromEntity(ent);
        List<String> callerRoles = roles(token);
        String callerUser        = username(token);

        if (!isElevated(callerRoles) && !callerUser.equals(ev.createdBy))
            return Response.status(Response.Status.FORBIDDEN).build();

        // Remover inscrições filhas
        Query<Key> qRegKeys = Query.newKeyQueryBuilder()
                                    .setKind("EventReg")
                                    .setFilter(PropertyFilter.hasAncestor(KF_EVENT.newKey(id)))
                                    .build();
        List<Key> toDelete = new ArrayList<>();
        DS.run(qRegKeys).forEachRemaining(toDelete::add);
        if (!toDelete.isEmpty()) DS.delete(toDelete.toArray(Key[]::new));

        // Remover o próprio evento
        DS.delete(KF_EVENT.newKey(id));
        return Response.noContent().build();
    }

    
    /* ============================================================= */
    /*        DELETE /events/{id}/register   (RU)                    */
    /* ============================================================= */
    @DELETE @Path("/{id}/register")
    public Response unregister(@HeaderParam("Authorization") String auth,
                               @PathParam("id") String id) {

        String token = jwt(auth);
        if (token == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        if (!roles(token).contains("RU"))
            return Response.status(Response.Status.FORBIDDEN).build();

        if (DS.get(KF_EVENT.newKey(id)) == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        Key regKey = KF_REG.addAncestor(PathElement.of("Event", id))
                           .newKey(username(token));

        if (DS.get(regKey) == null)
            return Response.status(Response.Status.NOT_FOUND).entity("Not registered").build();

        DS.delete(regKey);
        return Response.ok().build();
    }

    /* ============================================================= */
    /*               GET /events/{id}  (público)                     */
    /* ============================================================= */
    @GET @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        Entity ent = DS.get(KF_EVENT.newKey(id));
        return ent == null
               ? Response.status(Response.Status.NOT_FOUND).build()
               : Response.ok(Event.fromEntity(ent)).build();
    }

    /* ============================================================= */
    /*        GET /events/worksheet/{wsId}  (público)                */
    /* ============================================================= */
    @GET @Path("/worksheet/{wsId}")
    public Response listForWorksheet(@PathParam("wsId") String wsId) {

        if (!ExecutionSheetUtil.workSheetExists(wsId))
            return Response.status(Response.Status.NOT_FOUND).build();

        Query<Entity> q = Query.newEntityQueryBuilder()
                               .setKind("Event")
                               .setFilter(PropertyFilter.eq("workSheetId", wsId))
                               .build();

        List<Event> out = new ArrayList<>();
        DS.run(q).forEachRemaining(e -> out.add(Event.fromEntity(e)));
        return Response.ok(out).build();
    }

    /* ============================================================= */
    /*          POST /events/{id}/register   (RU)                    */
    /* ============================================================= */
    @POST @Path("/{id}/register")
    public Response register(@HeaderParam("Authorization") String auth,
                             @PathParam("id") String id) {

        String token = jwt(auth);
        if (token == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        if (!roles(token).contains("RU"))
            return Response.status(Response.Status.FORBIDDEN).build();

        // evento existe?
        if (DS.get(KF_EVENT.newKey(id)) == null)
            return Response.status(Response.Status.NOT_FOUND).build();

        // chave do registo é (Event,id) / EventReg / {username}
        Key regKey = KF_REG.addAncestor(PathElement.of("Event", id))
                           .newKey(username(token));

        if (DS.get(regKey) != null)
            return Response.status(Response.Status.CONFLICT)
                           .entity("Já inscrito").build();

        DS.put(new EventRegistration(id, username(token)).toEntity(DS));
        return Response.ok().build();
    }

    /* ============================================================= */
    /*  GET /events/{id}/registrations  (SYSBO|SYSADMIN ou criador)  */
    /* ============================================================= */
    @GET @Path("/{id}/registrations")
    public Response listRegistrations(@HeaderParam("Authorization") String auth,
                                      @PathParam("id") String id) {

        String token = jwt(auth);
        if (token == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        Entity evEnt = DS.get(KF_EVENT.newKey(id));
        if (evEnt == null) return Response.status(Response.Status.NOT_FOUND).build();
        Event ev = Event.fromEntity(evEnt);

        List<String> callerRoles = roles(token);
        String       callerUser  = username(token);

        if (!isElevated(callerRoles) && !callerUser.equals(ev.createdBy))
            return Response.status(Response.Status.FORBIDDEN).build();

        Query<Entity> q = Query.newEntityQueryBuilder()
                               .setKind("EventReg")
                               .setFilter(PropertyFilter.hasAncestor(KF_EVENT.newKey(id)))
                               .build();

        List<String> users = new ArrayList<>();
        DS.run(q).forEachRemaining(e -> users.add(e.getString("username")));
        return Response.ok(users).build();
    }

    /* ============================================================= */
    /*                GET /events/registered   (RU)                  */
    /* ============================================================= */
    @GET @Path("/registered")
    public Response listMyEvents(@HeaderParam("Authorization") String auth) {

        String token = jwt(auth);
        if (token == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        if (!roles(token).contains("RU"))
            return Response.status(Response.Status.FORBIDDEN).build();

        String user = username(token);

        Query<Entity> qReg = Query.newEntityQueryBuilder()
                .setKind("EventReg")
                .setFilter(PropertyFilter.eq("username", user))
                .build();

        List<Event> out = new ArrayList<>();
        DS.run(qReg).forEachRemaining(e -> {
            // ID do evento = ancestor da key  (Event,id)/EventReg/user
            Key ancestor = e.getKey().getParent();
            if (ancestor != null && "Event".equals(ancestor.getKind())) {
                Entity evEnt = DS.get(ancestor);
                if (evEnt != null) out.add(Event.fromEntity(evEnt));
            }
        });

        return Response.ok(out).build();
    }
}