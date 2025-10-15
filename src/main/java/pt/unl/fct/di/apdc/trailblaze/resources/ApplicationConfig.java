package pt.unl.fct.di.apdc.trailblaze.resources;

import java.util.HashSet;
import java.util.Set;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import pt.unl.fct.di.apdc.trailblaze.filters.CorsFilter;

@ApplicationPath("/rest")
public class ApplicationConfig extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();

        // ---------- resources ----------
        classes.add(AccountListingResource.class);
        classes.add(ActivateResource.class);
        classes.add(DeactivateResource.class);
        classes.add(ForceLogoutResource.class);
        classes.add(LoginJWTResource.class);
        classes.add(LoginResource.class);
        classes.add(LogoutJwtResource.class);
        classes.add(LogoutResource.class);
        classes.add(ProfileChangeResource.class);
        classes.add(RegisterResource.class);
        classes.add(RemoveAccountResource.class);
        classes.add(RemoveRequestResource.class);
        classes.add(SuspendResource.class);
        classes.add(UpdateAccountResource.class);
        classes.add(WorkSheetRemoveResource.class);
        classes.add(WorkSheetImportResource.class);
        classes.add(WorkSheetViewResource.class);
        classes.add(WorkSheetEditResource.class);
        classes.add(ExecutionSheetResource.class);
        classes.add(OperationResource.class);
        classes.add(OccurrenceResource.class);
        classes.add(MediaResource.class);
        classes.add(NotifyOutResource.class);
        classes.add(EventResource.class);
        classes.add(TrailResource.class);
        
        classes.add(CorsFilter.class);              
       

        return classes;
    }
}
