package au.org.ala.listsapi.config;

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
    public OpenApiCustomizer wsAliasCustomiser() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths != null) {
                Paths wsPaths = new Paths();
                paths.forEach((path, pathItem) -> {
                    if (path.startsWith("/v1/")) {
                        String wsPath = "/ws/" + path.substring(4);
                        
                        // Clone pathItem to avoid modifying the original and causing duplicate operationIds
                        // We use a manual shallow clone to preserve the exact Schema subclasses for parameters
                        io.swagger.v3.oas.models.PathItem clonedPathItem = new io.swagger.v3.oas.models.PathItem();
                        clonedPathItem.setSummary(pathItem.getSummary());
                        clonedPathItem.setDescription(pathItem.getDescription());
                        clonedPathItem.setServers(pathItem.getServers());
                        clonedPathItem.setParameters(pathItem.getParameters());
                        clonedPathItem.set$ref(pathItem.get$ref());
                        clonedPathItem.setExtensions(pathItem.getExtensions());

                        if (pathItem.getGet() != null) clonedPathItem.setGet(cloneOperation(pathItem.getGet()));
                        if (pathItem.getPut() != null) clonedPathItem.setPut(cloneOperation(pathItem.getPut()));
                        if (pathItem.getPost() != null) clonedPathItem.setPost(cloneOperation(pathItem.getPost()));
                        if (pathItem.getDelete() != null) clonedPathItem.setDelete(cloneOperation(pathItem.getDelete()));
                        if (pathItem.getOptions() != null) clonedPathItem.setOptions(cloneOperation(pathItem.getOptions()));
                        if (pathItem.getHead() != null) clonedPathItem.setHead(cloneOperation(pathItem.getHead()));
                        if (pathItem.getPatch() != null) clonedPathItem.setPatch(cloneOperation(pathItem.getPatch()));
                        if (pathItem.getTrace() != null) clonedPathItem.setTrace(cloneOperation(pathItem.getTrace()));

                        wsPaths.addPathItem(wsPath, clonedPathItem);
                    }
                });
                paths.putAll(wsPaths);
            }
        };
    }

    private io.swagger.v3.oas.models.Operation cloneOperation(io.swagger.v3.oas.models.Operation op) {
        io.swagger.v3.oas.models.Operation clone = new io.swagger.v3.oas.models.Operation();
        clone.setTags(op.getTags());
        clone.setSummary(op.getSummary());
        clone.setDescription(op.getDescription());
        clone.setExternalDocs(op.getExternalDocs());
        // Append _ws to ensure uniqueness
        if (op.getOperationId() != null) {
            clone.setOperationId(op.getOperationId() + "Ws");
        }
        clone.setParameters(op.getParameters());
        clone.setRequestBody(op.getRequestBody());
        clone.setResponses(op.getResponses());
        clone.setCallbacks(op.getCallbacks());
        clone.setDeprecated(op.getDeprecated());
        clone.setSecurity(op.getSecurity());
        clone.setServers(op.getServers());
        clone.setExtensions(op.getExtensions());
        return clone;
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