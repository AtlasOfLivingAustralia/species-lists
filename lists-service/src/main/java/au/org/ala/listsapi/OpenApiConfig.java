package au.org.ala.listsapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * OpenAPI configuration for the Lists API.
 */
@Configuration
public class OpenApiConfig {
    @Autowired
    private MessageSource messageSource;

    Locale locale = LocaleContextHolder.getLocale();

    @Value("${app.url}")
    private String appUrl;

    @Value("${springdoc.api-info.version:unknown}")
    private String apiVersion;

    /**
     * Customiser to remove trailing slashes from OpenAPI paths.
     * Mostly used for legacy v1 endpoints which should be accessible
     * with or without a trailing slash.
     * @return
     */
    @Bean
    public OpenApiCustomizer trailingSlashRemovalCustomiser() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths != null) {
                List<String> pathsToRemove = paths.keySet().stream()
                    .filter(path -> path.endsWith("/") && 
                            paths.containsKey(path.substring(0, path.length() - 1)))
                    .collect(Collectors.toList());
                
                pathsToRemove.forEach(paths::remove);
            }
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) version = apiVersion;

        // Sections of the API documentation
        // are ordered by the order in which they are added to this list.
        List<Tag> orderedTags = new ArrayList<>();
        orderedTags.add(new Tag().name(messageSource.getMessage("openapi.tags.restv2.name", null, locale))
                .description(messageSource.getMessage("openapi.tags.restv2.description", null, locale)));
        orderedTags.add(new Tag().name(messageSource.getMessage("openapi.tags.ingress.name", null, locale))
                .description(messageSource.getMessage("openapi.tags.ingress.description", null, locale)));
        orderedTags.add(new Tag().name(messageSource.getMessage("openapi.tags.validation.name", null, locale))
                .description(messageSource.getMessage("openapi.tags.validation.description", null, locale)));
        orderedTags.add(new Tag().name(messageSource.getMessage("openapi.tags.restv1.name", null, locale))
                .description(messageSource.getMessage("openapi.tags.restv1.description", null, locale)));

        return new OpenAPI()
                .info(new Info()
                        .title(messageSource.getMessage("openapi.info.title", new Object[]{appUrl}, locale))
                        .description(messageSource.getMessage("openapi.info.description", null, locale))
                        .version(version)
                        .contact(new Contact()
                                .name(messageSource.getMessage("openapi.info.institution.name", null, locale))
                                .email(messageSource.getMessage("openapi.info.institution.email", null, locale))
                                .url(messageSource.getMessage("openapi.info.institution.url", null, locale)))
                        .license(new License()
                                .name(messageSource.getMessage("openapi.info.institution.tou.title", null, locale))
                                .url(messageSource.getMessage("openapi.info.institution.tou.url", null, locale))))
                .externalDocs(new ExternalDocumentation()
                        .description(messageSource.getMessage("openapi.info.docs.description", null, locale))
                        .url(messageSource.getMessage("openapi.info.docs.url", null, locale)))
                .tags(orderedTags);
    }
}