package au.org.ala.listsapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

public class CorsTest {
    @Test
    public void testOrigins() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("https://*.ala.org.au"));
        
        System.out.println("Origin bie-test.ala.org.au matches: " + config.checkOrigin("https://bie-test.ala.org.au"));
        System.out.println("Origin bie.ala.org.au matches: " + config.checkOrigin("https://bie.ala.org.au"));
        System.out.println("Origin ala.org.au matches: " + config.checkOrigin("https://ala.org.au"));
    }
}
