package au.org.ala.listsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

@SpringBootApplication
@EnableAsync
@EnableMongoAuditing
@EnableScheduling
public class ListsApiApplication {

  private static final Logger logger = LoggerFactory.getLogger(ListsApiApplication.class);
  private static ConfigurableApplicationContext context;

  public static void main(String[] args) {
    context = SpringApplication.run(ListsApiApplication.class, args);
  }

  public static void restart() {
    ApplicationArguments args = context.getBean(ApplicationArguments.class);

    Thread thread = new Thread(() -> {
      context.close();
      context = SpringApplication.run(ListsApiApplication.class, args.getSourceArgs());
    });

    thread.setDaemon(false);
    thread.start();
  }

  @Bean(name = "processExecutor")
  public TaskExecutor workExecutor() {
    ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
    threadPoolTaskExecutor.setThreadNamePrefix("Async-");
    threadPoolTaskExecutor.setCorePoolSize(3);
    threadPoolTaskExecutor.setMaxPoolSize(3);
    threadPoolTaskExecutor.setQueueCapacity(100);
    threadPoolTaskExecutor.afterPropertiesSet();
    logger.info("ThreadPoolTaskExecutor set");
    return threadPoolTaskExecutor;
  }

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
    return messageSource;
  }

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
    return factory -> factory.addConnectorCustomizers(connector -> {
      // To allow encoded slashes (%2F)
      connector.setAllowTrace(true); // Example of another connector setting
      connector.setProperty("encodedSolidusHandling", "passthrough"); // or "decode" depending on needs

      // For encoded backslashes (%5C), if needed, though less common in URL paths
      // connector.setProperty("ALLOW_BACKSLASH", "true"); // Be cautious with this
    });
  }

}
