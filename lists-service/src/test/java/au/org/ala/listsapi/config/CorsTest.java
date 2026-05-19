package au.org.ala.listsapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test CORS origin pattern matching to verify wildcard subdomain patterns work correctly.
 * This ensures that requests from BIE and other ALA subdomains are properly allowed.
 */
public class CorsTest {
    
    @Test
    public void testOriginPatternMatching() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("https://*.ala.org.au"));
        
        // Test that subdomains match the wildcard pattern
        assertNotNull(config.checkOrigin("https://bie-test.ala.org.au"), 
            "Subdomain bie-test.ala.org.au should match pattern https://*.ala.org.au");
        assertNotNull(config.checkOrigin("https://bie.ala.org.au"), 
            "Subdomain bie.ala.org.au should match pattern https://*.ala.org.au");
        
        // Test that root domain does NOT match wildcard pattern (expected behavior)
        assertNull(config.checkOrigin("https://ala.org.au"), 
            "Root domain ala.org.au should NOT match *.ala.org.au pattern");
        
        // Test that non-ALA domains are rejected
        assertNull(config.checkOrigin("https://example.com"), 
            "Non-ALA domain should not match pattern");
        assertNull(config.checkOrigin("https://malicious.ala.org.au.evil.com"), 
            "Domain with ala.org.au as substring should not match");
    }
    
    @Test
    public void testMultipleOriginPatterns() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(
            "https://*.ala.org.au",
            "https://ala.org.au"  // Explicitly allow root domain if needed
        ));
        
        // Test that both subdomains and root domain match
        assertNotNull(config.checkOrigin("https://bie.ala.org.au"), 
            "Subdomain should match first pattern");
        assertNotNull(config.checkOrigin("https://ala.org.au"), 
            "Root domain should match second pattern");
    }
}
