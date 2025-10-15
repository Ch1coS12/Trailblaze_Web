package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;

import java.util.Date;
import java.util.UUID;

public class Operation {
    public String id;
    public String executionSheetId;
    public String name;
    public String parcelId;
    public String assignedOperator;
    public OperationState state;

    public Date predictedEndDate; // NOVO
    public Integer estimatedDurationMinutes; // NOVO

    public Operation() {}

    public Operation(String executionSheetId, String name, String parcelId, String assignedOperator) {
        this.id = UUID.randomUUID().toString();
        this.executionSheetId = executionSheetId;
        this.name = name;
        this.parcelId = parcelId;
        this.assignedOperator = assignedOperator;
        this.state = OperationState.PENDING;
    }

    public Entity toEntity(Datastore datastore) {
        Key key = datastore.newKeyFactory().setKind("Operation").newKey(id);
        Entity.Builder builder = Entity.newBuilder(key)
                .set("executionSheetId", executionSheetId)
                .set("name", name)
                .set("parcelId", parcelId)
                .set("assignedOperator", assignedOperator)
                .set("state", state.name());

        if (predictedEndDate != null)
            builder.set("predictedEndDate", predictedEndDate.getTime());

        if (estimatedDurationMinutes != null)
            builder.set("estimatedDurationMinutes", estimatedDurationMinutes);

        return builder.build();
    }

    public static Operation fromEntity(Entity entity) {
        Operation op = new Operation();
        op.id = entity.getKey().getName();
        op.executionSheetId = entity.getString("executionSheetId");
        op.name = entity.getString("name");
        op.parcelId = entity.getString("parcelId");
        op.assignedOperator = entity.getString("assignedOperator");
        op.state = OperationState.valueOf(entity.getString("state"));

        if (entity.contains("predictedEndDate"))
            op.predictedEndDate = new Date(entity.getLong("predictedEndDate"));

        if (entity.contains("estimatedDurationMinutes"))
            op.estimatedDurationMinutes = (int) entity.getLong("estimatedDurationMinutes");

        return op;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

}