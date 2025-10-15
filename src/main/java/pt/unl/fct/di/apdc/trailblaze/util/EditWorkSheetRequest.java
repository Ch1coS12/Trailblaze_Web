package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.List;

/**
 * Campos todos opcionais: apenas os presentes no JSON serão alterados.
 * Datas no formato ISO "yyyy-MM-dd".
 */
public class EditWorkSheetRequest {

    public String startingDate;
    public String finishingDate;
    public String issueDate;
    public String awardDate;
    public Long   serviceProviderId;

    public String posaCode;
    public String posaDescription;
    public String pospCode;
    public String pospDescription;

    /** Lista de polygonId a remover da folha (opcional) */
    public List<Integer> removePolygonIds;

    public EditWorkSheetRequest() {}
}
