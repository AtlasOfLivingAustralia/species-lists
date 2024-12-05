package au.org.ala.listsapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

  @Value("${app.url}")
  private String appUrl;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**").
        allowedOrigins("*").
        allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").
        allowedHeaders("*").
        allowCredentials(false).
        maxAge(3600);
  }
}
