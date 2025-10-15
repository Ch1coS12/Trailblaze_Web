package pt.unl.fct.di.apdc.trailblaze.util;

/**
 * Request para atualizar um trilho existente
 * Apenas permite adicionar observações
 */
public class UpdateTrailRequest {
    public String observation; // nova observação a adicionar

    public UpdateTrailRequest() {}

    /**
     * Verifica se há uma observação para adicionar
     */
    public boolean hasValidObservation() {
        return observation != null && !observation.trim().isEmpty();
    }
}