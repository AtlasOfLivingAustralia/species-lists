package au.org.ala.listsapi;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * OpenAPI configuration for the Lists API.
 */
@Configuration
@PropertySource(
        value = "file:///data/lists-service/config/lists-service-config.properties",
        ignoreResourceNotFound = true)
public class OpenApiConfig {

    @Value("${springdoc.api-info.version}")
    private String apiVersion;

    @Value("${app.url}")
    private String appUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Species Lists API")
                        .description("REST services for interacting with the ALA <a href='" + appUrl + "'>Species lists</a> application")
                        .version(apiVersion)
                        .contact(new Contact()
                                .name("ALA Support")
                                .email("support@ala.org.au")
                                .url("https://support.ala.org.au/support/solutions/6000137994"))
                        .license(new License()
                                .name("Terms of use")
                                .url("https://www.ala.org.au/terms-of-use/")))
                .externalDocs(new ExternalDocumentation()
                        .description("ALA API documentation and resources")
                        .url("https://docs.ala.org.au"));
    }
}