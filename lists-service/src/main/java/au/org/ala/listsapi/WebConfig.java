package au.org.ala.listsapi;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

  //@Value("${APP_URL}")
  //private String appUrl;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**").
        allowedOrigins("https://lists-develop.dev.ala.org.au").
        allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").
        allowedHeaders("*").
        allowCredentials(true).
        maxAge(3600);
  }
}
