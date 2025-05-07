package au.org.ala.listsapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

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

//  @Override
//  public void configurePathMatch(PathMatchConfigurer configurer) {
//    UrlPathHelper urlPathHelper = new UrlPathHelper();
//    // Set this to false to prevent decoding of the path before matching.
//    // This means your @PathVariable will receive the raw, encoded value.
//    urlPathHelper.setUrlDecode(true);
//    configurer.setUrlPathHelper(urlPathHelper);
//  }

  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    // Allow encoded slashes in URLs
    configurer.setUrlPathHelper(new UrlPathHelper());
  }

  @Bean
  public UrlPathHelper urlPathHelper() {
    UrlPathHelper urlPathHelper = new UrlPathHelper();
    // Don't decode URLs
    urlPathHelper.setUrlDecode(false);
    // Keep matrix variables
    urlPathHelper.setRemoveSemicolonContent(false);
    // Don't normalize paths with dots or double slashes
    urlPathHelper.setAlwaysUseFullPath(true);
    return urlPathHelper;
  }
}
