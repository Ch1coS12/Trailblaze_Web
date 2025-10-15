package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Representa um trilho criado por um utilizador RU.
 * Cada trilho pertence a uma WorkSheet (como parent) e contém pontos GPS.
 */
public class Trail {
    public String id;
    public String name;
    public String createdBy;
    public String worksheetId;
    public TrailVisibility visibility;
    public TrailStatus status; // apenas para trilhos privados
    public Date createdAt;
    public List<TrailPoint> points;
    public List<TrailObservation> observations; // múltiplas observações para trilhos públicos

    public Trail() {
        this.id = UUID.randomUUID().toString();
        this.visibility = TrailVisibility.PRIVATE;
        this.status = TrailStatus.ACTIVE; // apenas relevante para trilhos privados
        this.createdAt = new Date();
        this.points = new ArrayList<>();
        this.observations = new ArrayList<>();
    }

    public Trail(String name, String createdBy, String worksheetId) {
        this();
        this.name = name;
        this.createdBy = createdBy;
        this.worksheetId = worksheetId;
    }

    /**
     * Converte para Entity do Datastore com WorkSheet como parent
     */
    public Entity toEntity(Datastore datastore) {
        try {
        	 Key trailKey;
             if (worksheetId != null && !worksheetId.isBlank()) {
                 long wsId;
                 try {
                     wsId = Long.parseLong(worksheetId);
                 } catch (NumberFormatException e) {
                     throw new IllegalArgumentException("Invalid worksheetId format: " + worksheetId);
                 }
                 trailKey = datastore.newKeyFactory()
                    .addAncestor(PathElement.of("WorkSheet", wsId))
                    .setKind("Trail")
                    .newKey(id);
             } else {
                 trailKey = datastore.newKeyFactory().setKind("Trail").newKey(id);
             }
             
             Entity.Builder builder = Entity.newBuilder(trailKey)
                     .set("name", name)
                     .set("createdBy", createdBy)
                     .set("visibility", visibility.name())
                     .set("createdAt", Timestamp.of(createdAt));

             if (worksheetId != null && !worksheetId.isBlank()) {
                 builder.set("worksheetId", worksheetId);
             }

             if (visibility == TrailVisibility.PRIVATE) {
                 builder.set("status", status.name());
             }

             // Serializar pontos como JSON
             if (points != null && !points.isEmpty()) {
                 String pointsJson = new Gson().toJson(points);
                 builder.set("points", StringValue.newBuilder(pointsJson)
                         .setExcludeFromIndexes(true).build());
             } else {
                 builder.set("points", StringValue.newBuilder("[]")
                         .setExcludeFromIndexes(true).build());
             }
             // Serializar observações como JSON
             if (observations != null && !observations.isEmpty()) {
                 String observationsJson = new Gson().toJson(observations);
                 builder.set("observations", StringValue.newBuilder(observationsJson)
                         .setExcludeFromIndexes(true).build());
             } else {
                 builder.set("observations", StringValue.newBuilder("[]")
                         .setExcludeFromIndexes(true).build());
    }
             return builder.build();
        }catch (Exception e) {
            // Lida com erros inesperados (ou re-lança)
            throw new RuntimeException("Falha ao converter Trail em Entity", e);
        }
    }
             

    /**
     * Cria Trail a partir de Entity do Datastore
     */
    public static Trail fromEntity(Entity entity) {
        Trail trail = new Trail();
        trail.id = entity.getKey().getName();
        trail.name = entity.getString("name");
        trail.createdBy = entity.getString("createdBy");
        trail.worksheetId = entity.contains("worksheetId") ?
                entity.getString("worksheetId") : null;
        trail.visibility = TrailVisibility.valueOf(entity.getString("visibility"));
        trail.createdAt = entity.getTimestamp("createdAt").toDate();

        // Status apenas para trilhos privados
        if (trail.visibility == TrailVisibility.PRIVATE && entity.contains("status")) {
            trail.status = TrailStatus.valueOf(entity.getString("status"));
        }

        // Deserializar pontos do JSON
        String pointsJson = entity.getString("points");
        if (pointsJson != null && !pointsJson.isEmpty() && !pointsJson.equals("[]")) {
            try {
                Type pointListType = new TypeToken<List<TrailPoint>>(){}.getType();
                trail.points = new Gson().fromJson(pointsJson, pointListType);
                if (trail.points == null) {
                    trail.points = new ArrayList<>();
                }
            } catch (Exception e) {
                trail.points = new ArrayList<>();
            }
        } else {
            trail.points = new ArrayList<>();
        }

        // Deserializar observações do JSON
        String observationsJson = entity.getString("observations");
        if (observationsJson != null && !observationsJson.isEmpty() && !observationsJson.equals("[]")) {
            try {
                Type obsListType = new TypeToken<List<TrailObservation>>(){}.getType();
                trail.observations = new Gson().fromJson(observationsJson, obsListType);
                if (trail.observations == null) {
                    trail.observations = new ArrayList<>();
                }
            } catch (Exception e) {
                trail.observations = new ArrayList<>();
            }
        } else {
            trail.observations = new ArrayList<>();
        }

        return trail;
    }

    /**
     * Verifica se o utilizador pode ver este trilho
     */
    public boolean canBeViewedBy(String username) {
        if (createdBy.equals(username)) {
            return true; // Criador sempre pode ver
        }
        return visibility == TrailVisibility.PUBLIC; // Outros só veem se for público
    }

    /**
     * Verifica se o utilizador pode editar este trilho (adicionar observações)
     */
    public boolean canBeEditedBy(String username) {
        if (createdBy.equals(username)) {
            return true; // Criador sempre pode editar
        }
        return visibility == TrailVisibility.PUBLIC; // Trilhos públicos podem ser editados por qualquer RU
    }

    /**
     * Adiciona uma observação ao trilho
     */
    public void addObservation(String username, String observationText) {
        if (observations == null) {
            observations = new ArrayList<>();
        }
        observations.add(new TrailObservation(username, observationText));
    }
}