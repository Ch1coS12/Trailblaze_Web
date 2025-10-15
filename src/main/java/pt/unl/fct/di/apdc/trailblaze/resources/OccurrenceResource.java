package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.google.cloud.datastore.*;

import pt.unl.fct.di.apdc.trailblaze.util.*;

import java.util.*;

@Path("/occ")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OccurrenceResource {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();
    private static final KeyFactory OCC_KF = DS.newKeyFactory().setKind("Occurrence");

 
    private static String extractToken(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return auth.substring("Bearer ".length());
    }

  
    @POST
    @Path("/{sheetId}")
    public Response create(@HeaderParam("Authorization") String auth,
                           @PathParam("sheetId") String sheetId,
                           CreateOccurrenceRequest req) {

        String token = extractToken(auth);
        if (token == null) return Response.status(Status.UNAUTHORIZED).entity("Token ausente").build();

        String user = JwtUtil.getUsername(token);
        String role = JwtUtil.getUserRole(token);
        if (!"PO".equals(role))
            return Response.status(Status.FORBIDDEN).entity("Apenas PO pode criar ocorrências").build();

        if (req == null || !req.isValid())
            return Response.status(Status.BAD_REQUEST).entity("Pedido inválido").build();

        if (!ExecutionSheetUtil.executionSheetExists(sheetId))
            return Response.status(Status.NOT_FOUND).entity("Folha de execução não encontrada").build();

        Occurrence occ = new Occurrence();
        occ.id               = UUID.randomUUID().toString();
        occ.incidentType     = req.incidentType;
        occ.description      = req.description;
        occ.evidenceUrls     = req.evidenceUrls == null ? List.of() : req.evidenceUrls;
        occ.state            = OccurrenceState.PENDING;
        occ.creationTime     = new Date();
        occ.createdBy        = user;
        occ.executionSheetId = sheetId;

        DS.put(occ.toEntity(DS));
        return Response.status(Status.CREATED).entity(occ).build();
    }

  
    @GET
    @Path("/{id}")
    public Response get(@HeaderParam("Authorization") String auth,
                        @PathParam("id") String id) {
        String token = extractToken(auth);
        if (token == null) return Response.status(Status.UNAUTHORIZED).build();

        String user = JwtUtil.getUsername(token);
        String role = JwtUtil.getUserRole(token);
        if (user == null || role == null) return Response.status(Status.UNAUTHORIZED).build();

        Entity ent = DS.get(OCC_KF.newKey(id));
        if (ent == null) return Response.status(Status.NOT_FOUND).build();

        Occurrence occ = Occurrence.fromEntity(ent);

      
        boolean allowed = user.equals(occ.createdBy) || "PRBO".equals(role) || "SDVBO".equals(role);
        if (!allowed) return Response.status(Status.FORBIDDEN).build();

        return Response.ok(occ).build();
    }

    
    @GET
    @Path("/fe/{sheetId}")
    public Response list(@HeaderParam("Authorization") String auth,
                         @PathParam("sheetId") String sheetId) {
        String token = extractToken(auth);
        if (token == null) return Response.status(Status.UNAUTHORIZED).build();

        String user = JwtUtil.getUsername(token);
        String role = JwtUtil.getUserRole(token);
        if (user == null || role == null) return Response.status(Status.UNAUTHORIZED).build();

      
        if (!ExecutionSheetUtil.executionSheetExists(sheetId))
            return Response.status(Status.NOT_FOUND).build();

      
        StructuredQuery.PropertyFilter filter = StructuredQuery.PropertyFilter.eq("executionSheetId", sheetId);
        Query<Entity> q = Query.newEntityQueryBuilder()
                               .setKind("Occurrence")
                               .setFilter(filter)
                               .build();

        List<Occurrence> list = new ArrayList<>();
        DS.run(q).forEachRemaining(e -> {
            Occurrence o = Occurrence.fromEntity(e);
            if ("PRBO".equals(role) || "SDVBO".equals(role) || user.equals(o.createdBy))
                list.add(o);
        });

        return Response.ok(list).build();
    }

    
    @PUT
    @Path("/{id}/resolve")
    public Response resolve(@HeaderParam("Authorization") String auth,
                            @PathParam("id") String id,
                            ResolveOccurrenceRequest req) {
        String token = extractToken(auth);
        if (token == null) return Response.status(Status.UNAUTHORIZED).build();

        String user = JwtUtil.getUsername(token);
        String role = JwtUtil.getUserRole(token);
        if (!"PRBO".equals(role) && !"SDVBO".equals(role))
            return Response.status(Status.FORBIDDEN).build();

        Key key = OCC_KF.newKey(id);
        Entity ent = DS.get(key);
        if (ent == null) return Response.status(Status.NOT_FOUND).build();

        Occurrence occ = Occurrence.fromEntity(ent);
        if (occ.state == OccurrenceState.RESOLVED)
            return Response.status(Status.CONFLICT).entity("Já resolvida").build();

        occ.state          = OccurrenceState.RESOLVED;
        occ.resolvedBy     = user;
        occ.resolutionTime = new Date();
        if (req != null && req.resolutionComment != null && !req.resolutionComment.isBlank()) {
            occ.description += "\n\n[RESOLUÇÃO]: " + req.resolutionComment;
        }

        DS.put(occ.toEntity(DS));
        return Response.ok(occ).build();
    }
}
