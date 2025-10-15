package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

public final class ActiveJwtUtil {

    private static final Datastore ds = DatastoreOptions.getDefaultInstance().getService();

        public static void register(String username, String jti, long expMillis) {
        Key k = ds.newKeyFactory().setKind("JwtActive").newKey(jti);
        ds.put(Entity.newBuilder(k)
                     .set("username", username)
                     .set("exp", expMillis)
                     .build());
    }


    public static Iterable<Entity> listActiveForUser(String username) {
        Query<Entity> q = Query.newEntityQueryBuilder()
                .setKind("JwtActive")
                .setFilter(StructuredQuery.PropertyFilter.eq("username", username))
                .build();
        return () -> ds.run(q);
    }


    public static void delete(Key k) { ds.delete(k); }
}