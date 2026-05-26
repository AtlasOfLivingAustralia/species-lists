package au.org.ala.listsapi.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Redirects legacy pre-version requests to the new v2 API paths.
 * Handles URL redirects from:
 * - "/upload", "/ingest", "/delete", "/constraints" to "/v2/upload", "/v2/ingest", "/v2/delete", "/v2/constraints"
 * - "/speciesListItem/downloadList/{listId}" to "/v2/download/{listId}"
 */
@Component
public class LegacyRedirectFilter extends OncePerRequestFilter {

    private final String v2Prefix;
    private final Map<String, String> preVersionPaths;
    private static final Logger logger = LoggerFactory.getLogger(LegacyRedirectFilter.class);

    public LegacyRedirectFilter(
            @Value("${api.v2.prefix:/v2}") String v2Prefix,
            @Value("${api.preversion.upload:/upload}") String preversionUpload,
            @Value("${api.preversion.ingest:/ingest}") String preversionIngest,
            @Value("${api.preversion.delete:/delete}") String preversionDelete,
            @Value("${api.preversion.constraints:/constraints}") String preversionConstraints) {

        this.v2Prefix = v2Prefix;

        // Create a map of preversion paths 
        this.preVersionPaths = new HashMap<>();
        this.preVersionPaths.put(preversionUpload, preversionUpload);
        this.preVersionPaths.put(preversionIngest, preversionIngest);
        this.preVersionPaths.put(preversionDelete, preversionDelete);
        this.preVersionPaths.put(preversionConstraints, preversionConstraints);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // Handle pre-version paths (Biocollect, etc.)
        for (Map.Entry<String, String> entry : preVersionPaths.entrySet()) {
            String path = entry.getKey();

            if (requestUri.startsWith(path)) {
                String queryString = request.getQueryString();
                String newUriWithParams = requestUri.replaceFirst(path, v2Prefix + path) + (queryString != null ? "?" + queryString : "");
                logger.debug("Redirecting pre-version path: " + requestUri + " to " + newUriWithParams);
                redirect(response, newUriWithParams);
                return;
            }
        }

        // Handle one-off redirects for specific endpoints
        if (requestUri.contains("/speciesListItem/downloadList")) {
            String queryString = request.getQueryString();
            // Get the path variable 'listId' from the request path
            String pathVariable = requestUri.substring(requestUri.lastIndexOf('/') + 1);
            String newUri = v2Prefix + "/download/" + pathVariable + (queryString != null ? "?" + queryString : "");
            redirect(response, newUri);
            return;
        }

        // For non-matching requests, continue with the filter chain
        filterChain.doFilter(request, response);
    }

    private void redirect(HttpServletResponse response, String newUri) throws IOException {
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", newUri);
    }
}