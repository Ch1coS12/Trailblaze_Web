package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

public class ExecutionSheetUtil {
    private static final Datastore datastore = DatastoreOptions.getDefaultInstance().getService();

    public static boolean workSheetExists(String workSheetId) {
        if (workSheetId == null || workSheetId.isBlank()) return false;
        try {
            long id = Long.parseLong(workSheetId);  // ← conversão
            Key key = datastore.newKeyFactory().setKind("WorkSheet").newKey(id);
            return datastore.get(key) != null;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    public static boolean executionSheetExists(String sheetId) {
        if (sheetId == null || sheetId.isBlank()) return false;
        Key key = datastore.newKeyFactory().setKind("ExecutionSheet").newKey(sheetId);
        return datastore.get(key) != null;
    }

}