package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

public final class TokenBlacklistUtil {

    private static final Datastore datastore =
            DatastoreOptions.getDefaultInstance().getService();

    private TokenBlacklistUtil() { }


    public static void blacklist(String jti, long expMillis) {
        Key key = datastore.newKeyFactory().setKind("JwtBlacklist").newKey(jti);
        datastore.put(Entity.newBuilder(key).set("exp", expMillis).build());
    }


    public static boolean isBlacklisted(String jti) {
    	   Entity e = datastore.get(datastore.newKeyFactory().setKind("JwtBlacklist").newKey(jti));
    	    if (e == null) return false;

    	    long exp = e.getLong("exp");               // ← usa getLong
    	    // só é inválido se **já** passou
    	    return exp <= System.currentTimeMillis();
    }
}