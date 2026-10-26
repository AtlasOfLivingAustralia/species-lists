package au.org.ala.listsapi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for the integration tests that start the whole application, with a real MongoDB and a
 * real Elasticsearch, and call it over HTTP through MockMvc.
 *
 * <p>These are the only tests that prove the application context starts at all, and the only ones
 * that see what a client sees: a real servlet container on a random port, the JSON Jackson writes,
 * the status codes, and the filter chain.
 *
 * <p>Authentication is off, as it is for a deployment with {@code security.jwt.enabled=false}: a
 * test that signed a user in would have to reach the real identity provider. Authorisation is
 * unaffected, since it reads the principal, so the tests still cover an anonymous caller being
 * refused a private list. The client credentials are only read to build the token service bean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractApplicationIT {

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", MongoTestDataStore::connectionString);
        registry.add("spring.mongodb.database", () -> "species-lists-it");

        registry.add("elastic.host", ElasticTestDataStore::httpHostAddress);
        registry.add("spring.elasticsearch.uris", () -> "http://" + ElasticTestDataStore.httpHostAddress());
        registry.add("elastic.tls.enabled", () -> "false");
        registry.add("elastic.auth.enabled", () -> "false");
        registry.add("elastic.username", () -> "");
        registry.add("elastic.password", () -> "");

        registry.add("webservice.client-id", () -> "test-client");
        registry.add("webservice.client-secret", () -> "test-secret");

        registry.add("security.jwt.enabled", () -> "false");
        registry.add("security.oidc.enabled", () -> "false");
        registry.add("security.cas.enabled", () -> "false");
        registry.add("security.apikey.enabled", () -> "false");

        registry.add("aws.s3.enabled", () -> "false");
        registry.add("release.s3.enabled", () -> "false");
    }
}
