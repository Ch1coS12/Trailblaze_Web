package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class Occurrence {
    public String id;
    public String incidentType;
    public String description;
    public List<String> evidenceUrls = new ArrayList<>();
    public OccurrenceState state;
    public Date creationTime;
    public Date resolutionTime;
    public String createdBy;
    public String resolvedBy;
    public String executionSheetId;   

    public Occurrence() {}

   
    public Entity toEntity(Datastore ds) {
        KeyFactory kf = ds.newKeyFactory()
                          .addAncestor(PathElement.of("ExecutionSheet", executionSheetId))
                          .setKind("Occurrence");
        Key key = kf.newKey(id);

        Entity.Builder b = Entity.newBuilder(key)
                .set("incidentType", incidentType)
                .set("description", description)
                .set("state", state.name())
                .set("createdBy", createdBy)
                .set("creationTime", creationTime.getTime())
                .set("executionSheetId", executionSheetId);  

        if (evidenceUrls != null && !evidenceUrls.isEmpty()) {
            List<Value<?>> list = new ArrayList<>();
            for (String url : evidenceUrls)
                list.add(StringValue.newBuilder(url).setExcludeFromIndexes(true).build());
            b.set("evidenceUrls", ListValue.of(list));
        }
        if (resolvedBy != null) {
            b.set("resolvedBy", resolvedBy);
            b.set("resolutionTime", resolutionTime == null ? 0L : resolutionTime.getTime());
        }
        return b.build();
    }

    public static Occurrence fromEntity(Entity e) {
        Occurrence o = new Occurrence();
        o.id           = e.getKey().getName();
        o.incidentType = e.getString("incidentType");
        o.description  = e.getString("description");
        if (e.contains("evidenceUrls")) {
            o.evidenceUrls = new ArrayList<>();
            for (Value<?> v : e.getList("evidenceUrls"))
                o.evidenceUrls.add(((StringValue) v).get());
        }
        o.state        = OccurrenceState.valueOf(e.getString("state"));
        o.createdBy    = e.getString("createdBy");
        o.creationTime = new Date(e.getLong("creationTime"));


        if (e.getKey().getParent() != null)
            o.executionSheetId = e.getKey().getParent().getName();
        else if (e.contains("executionSheetId"))
            o.executionSheetId = e.getString("executionSheetId");

        if (e.contains("resolvedBy")) {
            o.resolvedBy    = e.getString("resolvedBy");
            long rt         = e.getLong("resolutionTime");
            o.resolutionTime = rt > 0 ? new Date(rt) : null;
        }
        return o;
    }
}