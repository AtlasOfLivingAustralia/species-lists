package au.org.ala.listsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
@EnableMongoAuditing
@PropertySource(
    value = "file:///data/lists-service/config/lists-service-config.properties",
    ignoreResourceNotFound = true)
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
}
