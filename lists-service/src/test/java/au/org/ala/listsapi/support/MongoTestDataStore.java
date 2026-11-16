package au.org.ala.listsapi.support;

import org.testcontainers.mongodb.MongoDBContainer;

/**
 * One MongoDB for the whole test JVM, from the same image as src/main/docker/docker-compose.yml, so
 * the integration tests run against the version we deploy.
 *
 * <p>The container starts when this class is first loaded, which is on the first call from a
 * {@code @DynamicPropertySource} method. That only runs for a test class JUnit has not disabled, so
 * a machine without Docker skips the integration tests (see {@link AbstractMongoIT}) instead of
 * failing to start a container. Testcontainers' own reaper stops it when the JVM exits.
 */
public final class MongoTestDataStore {

    private static final MongoDBContainer CONTAINER = new MongoDBContainer("mongo:8.0");

    static {
        CONTAINER.start();
    }

    private MongoTestDataStore() {
        // Static access only
    }

    /** Connection string for a database of its own, so no test sees another's documents. */
    public static String connectionString() {
        return CONTAINER.getConnectionString();
    }
}
