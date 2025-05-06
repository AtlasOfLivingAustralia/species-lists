package au.org.ala.listsapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {
  /**
   * The URL of the application, used for CORS configuration.
   * This should be set to the URL of the application in production.
   * This property is loaded from the lists-service-config.properties file but if there is an
   * entry in application.properties, it will override the value in the ext. config file.
   */
  @Value("${app.url}")
  private String appUrl;

  /**
   * CORS configuration for the application.
   * 
   * @param registry the CORS registry to configure
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String allowedOrigins = appUrl;

    registry.addMapping("/**").
        allowedOrigins(allowedOrigins).
        allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").
        allowedHeaders("*").
        allowCredentials(false).
        maxAge(3600);
  }
}
