package au.org.ala.listsapi.support;

import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for the MongoDB integration tests: a real MongoDB in a container and only the Mongo
 * part of the application context, so nothing here needs the security, Elasticsearch or AWS
 * configuration the full application has.
 *
 * <p>These tests cover what a Mockito test cannot: whether a repository's query, criteria or SpEL
 * expression means to MongoDB what it is meant to mean. They are named {@code *IT} so Failsafe runs
 * them (`mvn verify`) and Surefire's {@code mvn test} stays free of containers.
 *
 * <p>{@code disabledWithoutDocker} skips them on a machine with no Docker rather than failing.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractMongoIT {

    /**
     * ListsApiApplication declares a CommandLineRunner that takes one, and the Mongo slice has no
     * Elasticsearch. Nothing runs a CommandLineRunner in a test context, so a mock is enough.
     */
    @MockitoBean private ElasticsearchOperations elasticsearchOperations;

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        // Boot 4 reads spring.mongodb.*, as the deployed configuration does
        registry.add("spring.mongodb.uri", MongoTestDataStore::connectionString);
        registry.add("spring.mongodb.database", () -> "species-lists-it");
    }
}
