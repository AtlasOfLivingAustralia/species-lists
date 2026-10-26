package au.org.ala.listsapi.support;

import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * One Elasticsearch for the whole test JVM, from the same image as
 * src/main/docker/docker-compose.yml. Security is off, as it is for the local docker-compose and for
 * {@code elastic.auth.enabled=false}, so the tests reach it over plain HTTP.
 *
 * <p>Started lazily, for the same reason as {@link MongoTestDataStore}.
 */
public final class ElasticTestDataStore {

    private static final ElasticsearchContainer CONTAINER =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.17.0")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node")
                    // Enough for a test index, and far less than the container's default heap
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static {
        CONTAINER.start();
    }

    private ElasticTestDataStore() {
        // Static access only
    }

    /** "host:port", the form {@code elastic.host} takes. */
    public static String httpHostAddress() {
        return CONTAINER.getHttpHostAddress().replace("http://", "");
    }
}
