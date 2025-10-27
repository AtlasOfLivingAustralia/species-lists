package au.org.ala.listsapi;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.springframework.stereotype.Component;

/**
 * Provides the application version by reading it from the pom.properties file.
 * Required for injecting the version into the OpenAPI documentation when
 * the application is packaged as a jar and deployed to k8s
 */
@Component
public class VersionProvider {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VersionProvider.class);
    /**
     * Get the application version from the pom.properties file.
     * @return the application version
     */
    public String getVersion() {
        // 1️⃣ Try to read from pom.properties (for packaged JARs)
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/maven/com.example/my-app/pom.properties")) {
            if (inputStream != null) {
                Properties props = new Properties();
                props.load(inputStream);
                return props.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            logger.warn("Unable to read version from pom.properties", e);
        }

        // 2️⃣ Fallback: read from pom.xml (for mvn spring-boot:run)
        try (FileReader reader = new FileReader("pom.xml")) {
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            Model model = mavenReader.read(reader);
            if (model != null && model.getVersion() != null) {
                return model.getVersion();
            }
        } catch (Exception e) {
            logger.warn("Unable to read version from pom.xml", e);
        
        }

        return "unknown";
    }
}