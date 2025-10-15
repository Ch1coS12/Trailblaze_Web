package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

public class EventRegistration {
    public String eventId;
    public String username;

    public EventRegistration() {}

    public EventRegistration(String eventId, String username) {
        this.eventId = eventId;
        this.username = username;
    }

    public Entity toEntity(Datastore ds) {
        KeyFactory kf = ds.newKeyFactory()
                .addAncestor(PathElement.of("Event", eventId))
                .setKind("EventReg");
        Key key = kf.newKey(username);
        return Entity.newBuilder(key)
                .set("username", username)
                .set("eventId", eventId)
                .build();
    }

    public static EventRegistration fromEntity(Entity e) {
        EventRegistration r = new EventRegistration();
        r.username = e.getString("username");
        if (e.getKey().getParent() != null)
            r.eventId = e.getKey().getParent().getName();
        else if (e.contains("eventId"))
            r.eventId = e.getString("eventId");
        return r;
    }
}
