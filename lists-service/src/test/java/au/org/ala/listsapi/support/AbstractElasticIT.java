package au.org.ala.listsapi.support;

import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import au.org.ala.listsapi.config.Config;

/**
 * Base class for the Elasticsearch integration tests: a real Elasticsearch in a container, the
 * Elasticsearch part of the application context, and the application's own {@link Config}.
 *
 * <p>{@link Config} rather than Spring Boot's auto-configured client on purpose: it is what rewrites
 * the client's {@code compatible-with=9} headers to 8, which is the only way the Elasticsearch 9
 * client in our dependency tree can talk to the 8.17 server we deploy. A test that used the
 * auto-configured client would prove nothing about production.
 */
@DataElasticsearchTest
@Import({Config.class, AbstractElasticIT.MongoAuditingSupport.class})
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractElasticIT {

    /**
     * ListsApiApplication is annotated {@code @EnableMongoAuditing}, whose handler asks for a
     * mongoMappingContext bean that the Elasticsearch slice does not create. An empty context is
     * enough: these tests touch no MongoDB document.
     *
     * <p>{@code @TestConfiguration} rather than {@code @Configuration} on purpose. The application
     * is scanned from au.org.ala.listsapi, which covers this package, and the test classes are on
     * the classpath: as a plain configuration it would also be picked up by the full application
     * context, where it would replace the auto-configured mapping context with one that has no
     * simple types, and the context would fail to start on java.time.LocalDateTime. Boot leaves
     * {@code @TestConfiguration} out of component scanning.
     */
    @TestConfiguration
    static class MongoAuditingSupport {

        @Bean
        MongoMappingContext mongoMappingContext() {
            return new MongoMappingContext();
        }
    }

    @DynamicPropertySource
    static void elasticProperties(DynamicPropertyRegistry registry) {
        registry.add("elastic.host", ElasticTestDataStore::httpHostAddress);
        registry.add("spring.elasticsearch.uris", () -> "http://" + ElasticTestDataStore.httpHostAddress());
        // The container runs without TLS or authentication, as docker-compose does locally
        registry.add("elastic.tls.enabled", () -> "false");
        registry.add("elastic.auth.enabled", () -> "false");
        registry.add("elastic.username", () -> "");
        registry.add("elastic.password", () -> "");
        registry.add("elastic.maximumDocuments", () -> "10000");
    }
}
