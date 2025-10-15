package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

import java.util.Date;

public class Event {
    public String id;
    public String title;
    public String description;
    public Date dateTime;
    public String location;
    public String workSheetId;
    public String createdBy;

    public Event() {}

    public Entity toEntity(Datastore ds) {
        Key key = ds.newKeyFactory().setKind("Event").newKey(id);
        Entity.Builder b = Entity.newBuilder(key)
                .set("title", title)
                .set("description", StringValue.newBuilder(description).setExcludeFromIndexes(true).build())
                .set("dateTime", dateTime.getTime())
                .set("location", location)
                .set("workSheetId", workSheetId)
                .set("createdBy", createdBy);
        return b.build();
    }

    public static Event fromEntity(Entity e) {
        Event ev = new Event();
        ev.id = e.getKey().getName();
        ev.title = e.getString("title");
        ev.description = e.getString("description");
        ev.dateTime = new Date(e.getLong("dateTime"));
        ev.location = e.getString("location");
        ev.workSheetId = e.getString("workSheetId");
        ev.createdBy = e.getString("createdBy");
        return ev;
    }
}
