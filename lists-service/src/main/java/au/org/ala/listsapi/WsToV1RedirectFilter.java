package au.org.ala.listsapi;

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
 * Redirects legacy requests to the new API version paths.
 * Handles redirects from:
 * - "/ws/" to "/v1/"
 * - "/upload", "/ingest", "/delete" to "/v2/upload", "/v2/ingest", "/v2/delete"
 * - Pre-versioned paths to their versioned equivalents
 */
@Component
public class WsToV1RedirectFilter extends OncePerRequestFilter {

    private final String wsPrefix;
    private final String v1Prefix;
    private final String v2Prefix;
    private final Map<String, String> preVersionPaths;
    private static final Logger logger = LoggerFactory.getLogger(WsToV1RedirectFilter.class);

    public WsToV1RedirectFilter(
            @Value("${api.ws.prefix:/ws}") String wsPrefix,
            @Value("${api.v1.prefix:/v1}") String v1Prefix,
            @Value("${api.v2.prefix:/v2}") String v2Prefix,
            @Value("${api.preversion.upload:/upload}") String preversionUpload,
            @Value("${api.preversion.ingest:/ingest}") String preversionIngest,
            @Value("${api.preversion.delete:/delete}") String preversionDelete,
            @Value("${api.preversion.constraints:/constraints}") String preversionConstraints) {

        this.wsPrefix = wsPrefix;
        this.v1Prefix = v1Prefix;
        this.v2Prefix = v2Prefix;

        // Create a map of preversion paths for cleaner code
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

        // Handle old prefix redirect
        if (requestUri.startsWith(wsPrefix)) {
            String queryString = request.getQueryString();
            String newUri = requestUri.replaceFirst(wsPrefix, v1Prefix);
            if (queryString != null) {
                newUri += "?" + queryString;
            }
            redirect(response, newUri);
            return;
        }

        // Handle pre-version paths (Biocollect, etc.)
        for (Map.Entry<String, String> entry : preVersionPaths.entrySet()) {
            String path = entry.getKey();
            logger.debug("Request URI: " + requestUri + "; with path: " + path + "; with query: " + request.getQueryString());

            if (requestUri.startsWith(path)) {
                String queryString = request.getQueryString();
                String newUriWithParams = requestUri.replaceFirst(path, v2Prefix + path) + (queryString != null ? "?" + queryString : "");
                logger.debug("Redirecting pre-version path: " + requestUri + " to " + newUriWithParams);
                redirect(response, newUriWithParams);
                return;
            }
        }

        // For non-matching requests, continue with the filter chain
        filterChain.doFilter(request, response);
    }

    private void redirect(HttpServletResponse response, String newUri) throws IOException {
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", newUri);
    }
}