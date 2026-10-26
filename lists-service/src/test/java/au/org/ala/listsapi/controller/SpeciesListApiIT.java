package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.support.AbstractApplicationIT;

/**
 * The v2 species list endpoints over HTTP, against the running application. Plain HTTP calls rather
 * than MockMvc, so what is asserted is the bytes a client receives.
 */
class SpeciesListApiIT extends AbstractApplicationIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort private int port;

    @Autowired private SpeciesListMongoRepository listRepository;

    @BeforeEach
    void seedLists() {
        listRepository.deleteAll();
        listRepository.save(
                SpeciesList.builder()
                        .id("dr1")
                        .dataResourceUid("dr1-uid")
                        .title("Threatened flora")
                        .listType("CONSERVATION_LIST")
                        .isPrivate(false)
                        .owner("alice")
                        .rowCount(2)
                        .build());
        listRepository.save(
                SpeciesList.builder()
                        .id("dr2")
                        .dataResourceUid("dr2-uid")
                        .title("Alice's private list")
                        .isPrivate(true)
                        .owner("alice")
                        .build());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void getSpeciesList_returnsTheListAsJson() throws Exception {
        HttpResponse<String> response = get("/v2/speciesList/dr1");

        assertEquals(200, response.statusCode());
        assertTrue(
                response.headers().firstValue("content-type").orElse("").startsWith("application/json"));

        JsonNode body = JSON.readTree(response.body());
        assertEquals("dr1", body.get("id").asText());
        assertEquals("Threatened flora", body.get("title").asText());
        assertEquals("CONSERVATION_LIST", body.get("listType").asText());
    }

    @Test
    void getSpeciesList_writesDatesAsEpochMilliseconds() throws Exception {
        // Issue #851: these went out as ISO strings once, and the clients read them as numbers.
        // spring.jackson.datatype.datetime.write-dates-as-timestamps is what keeps them numeric, and
        // a response is the only place that shows it. The value itself is set by Mongo auditing
        // (@CreatedDate), so it is compared with what the database holds rather than a fixed date.
        Date stored = listRepository.findById("dr1").orElseThrow().getDateCreated();

        JsonNode body = JSON.readTree(get("/v2/speciesList/dr1").body());

        assertTrue(
                body.get("dateCreated").isNumber(),
                "dateCreated should be a number, was " + body.get("dateCreated"));
        assertEquals(stored.getTime(), body.get("dateCreated").asLong());
    }

    @Test
    void getSpeciesList_acceptsTheDataResourceUidAsWellAsTheId() throws Exception {
        assertEquals(200, get("/v2/speciesList/dr1-uid").statusCode());
    }

    @Test
    void getSpeciesList_refusesAPrivateListToAnAnonymousCaller() throws Exception {
        assertEquals(403, get("/v2/speciesList/dr2").statusCode());
    }

    @Test
    void getSpeciesList_returns404ForAnUnknownList() throws Exception {
        assertEquals(404, get("/v2/speciesList/dr-does-not-exist").statusCode());
    }
}
