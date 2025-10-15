package pt.unl.fct.di.apdc.trailblaze.resources;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import pt.unl.fct.di.apdc.trailblaze.util.HashUtil;

import com.google.cloud.datastore.*;
import java.util.List;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

@WebListener
public class StartupListener implements ServletContextListener {

    private static final String ROOT_USERNAME = "root";
    private static final String PASSWORD_FILE_RELATIVE_PATH = "config/root-password.txt";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[INIT] A iniciar criação automática da conta root...");

        try {
            Datastore datastore = DatastoreOptions.getDefaultInstance().getService();
            Key userKey = datastore.newKeyFactory().setKind("Account").newKey(ROOT_USERNAME);

            if (datastore.get(userKey) != null) {
                System.out.println("[INIT] Conta root já existe.");
                return;
            }

            String password = "root123";

            List<Value<?>> roles = List.of(
                    StringValue.of("SYSADMIN"),
                    StringValue.of("SGVBO"),
                    StringValue.of("SDVBO")
            );
            
            Entity entity = Entity.newBuilder(userKey)
                    .set("email", "root@trailblaze.pt")
                    .set("password", HashUtil.hashPassword(password))
                    .set("name", "Administrador Root")
                    .set("roles", ListValue.of(roles))
                    .set("state", "ATIVADA")
                    .set("profile", "PRIVADO")
                    .build();

            datastore.put(entity);
            System.out.println("[INIT] Conta root criada com sucesso.");

        } catch (Exception e) {
            System.err.println("[INIT ERROR] Falha ao criar conta root: " + e.getMessage());
        }
    }

    /*
    private String readPasswordFromFile() {
        try {
            String projectDir = System.getProperty("user.dir");
            String fullPath = Paths.get(projectDir, PASSWORD_FILE_RELATIVE_PATH).toString();
            System.out.println("[INIT] A ler password de: " + fullPath);

            try (BufferedReader reader = new BufferedReader(new FileReader(fullPath))) {
                return reader.readLine().trim();
            }
        } catch (IOException e) {
            System.err.println("[INIT ERROR] Erro a ler ficheiro de password: " + e.getMessage());
            return null;
        }
    }

*/
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nada a fazer no shutdown
    }
}
