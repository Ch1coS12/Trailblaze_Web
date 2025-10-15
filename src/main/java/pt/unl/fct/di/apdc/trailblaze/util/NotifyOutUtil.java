package pt.unl.fct.di.apdc.trailblaze.util;

import com.google.cloud.datastore.*;

import java.util.HashSet;
import java.util.Set;

public class NotifyOutUtil {

    private static final Datastore DS = DatastoreOptions.getDefaultInstance().getService();

    /**
     * Função 1: Notificar o PRBO da conclusão duma operação numa parcela específica
     * Chamada quando uma atividade termina numa parcela
     */
    public static void checkAndNotifyOperationParcelEnd(String operationId, String parcelId) {
        // Verificar se todas as atividades desta operação nesta parcela terminaram
        Query<Entity> q = Query.newEntityQueryBuilder()
                .setKind("Activity")
                .setFilter(StructuredQuery.CompositeFilter.and(
                        StructuredQuery.PropertyFilter.eq("operationId", operationId),
                        StructuredQuery.PropertyFilter.eq("parcelId", parcelId)))
                .build();

        QueryResults<Entity> activities = DS.run(q);
        while (activities.hasNext()) {
            Entity activity = activities.next();
            // Se encontrar alguma atividade sem endTime, ainda não terminou
            if (!activity.contains("endTime") || activity.getTimestamp("endTime") == null) {
                return; // Ainda há atividades em curso
            }
        }

        // Todas as atividades desta operação nesta parcela terminaram
        // Encontrar o PRBO responsável
        String prboUsername = findPRBOForOperation(operationId);
        if (prboUsername != null) {
            createNotification(
                prboUsername,
                "Operação concluída na parcela",
                String.format("A operação %s foi concluída na parcela %s", operationId, parcelId)
            );
        }
    }

    /**
     * Função 2: Notificar o PRBO da conclusão duma operação em todas as parcelas
     * Chamada quando uma operação é totalmente concluída
     */
    public static void checkAndNotifyOperationEnd(String operationId) {
        // Buscar todas as ParcelOperationExecutions desta operação
        Query<Entity> parcelQuery = Query.newEntityQueryBuilder()
                .setKind("ParcelOperationExecution")
                .setFilter(StructuredQuery.PropertyFilter.eq("operationExecutionId", operationId))
                .build();

        QueryResults<Entity> parcels = DS.run(parcelQuery);
        boolean allCompleted = true;

        while (parcels.hasNext()) {
            Entity parcel = parcels.next();
            String status = parcel.getString("status");
            
            // Se alguma parcela não estiver EXECUTED, a operação não está completa
            if (!"EXECUTED".equals(status)) {
                allCompleted = false;
                break;
            }
        }

        if (allCompleted) {
            // Todas as parcelas da operação estão concluídas
            String prboUsername = findPRBOForOperation(operationId);
            if (prboUsername != null) {
                createNotification(
                    prboUsername,
                    "Operação totalmente concluída",
                    String.format("A operação %s foi concluída em todas as parcelas da folha de obra", operationId)
                );
            }
        }
    }

    /**
     * Encontra o PRBO responsável por uma operação
     */
    private static String findPRBOForOperation(String operationId) {
        try {
            // 1. Buscar a OperationExecution
            Query<Entity> opExecQuery = Query.newEntityQueryBuilder()
                    .setKind("OperationExecution")
                    .setFilter(StructuredQuery.PropertyFilter.eq("operationId", operationId))
                    .build();

            QueryResults<Entity> opExecResults = DS.run(opExecQuery);
            if (!opExecResults.hasNext()) {
                return null;
            }

            Entity opExec = opExecResults.next();
            String executionSheetId = opExec.getString("executionSheetId");

            // 2. Buscar a ExecutionSheet
            Key execSheetKey = DS.newKeyFactory().setKind("ExecutionSheet").newKey(executionSheetId);
            Entity execSheet = DS.get(execSheetKey);
            
            if (execSheet != null) {
                return execSheet.getString("associatedUser"); // Este é o PRBO
            }

        } catch (Exception e) {
            System.err.println("Erro ao encontrar PRBO para operação " + operationId + ": " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Cria uma notificação para um utilizador
     */
    private static void createNotification(String userId, String title, String message) {
        try {
            Key notificationKey = DS.allocateId(DS.newKeyFactory().setKind("Notification").newKey());
            
            Entity notification = Entity.newBuilder(notificationKey)
                    .set("userId", userId)
                    .set("title", title)
                    .set("message", message)
                    .set("timestamp", System.currentTimeMillis())
                    .set("read", false)
                    .build();

            DS.put(notification);
            
            System.out.println("Notificação criada para " + userId + ": " + title);
            
        } catch (Exception e) {
            System.err.println("Erro ao criar notificação: " + e.getMessage());
        }
    }
}