package pt.unl.fct.di.apdc.trailblaze.util;

import java.util.Date;
import java.util.List;

public class UpdateActivityRequest {
    public String id; // ID da atividade
    public String operatorId;
    public Date startTime;
    public Date endTime;
    public String observations;
    public String gpsTrack;
    public List<String> photoUrls;
}
