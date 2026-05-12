package au.org.ala.listsapi.util;

import static org.junit.jupiter.api.Assertions.*;

import au.org.ala.listsapi.model.Filter;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchQueryBuilderTest {

  @Test
  void testCleanRawQuery() {
    assertEquals("", ElasticsearchQueryBuilder.cleanRawQuery(null));
    assertEquals("", ElasticsearchQueryBuilder.cleanRawQuery("  "));
    assertEquals("test", ElasticsearchQueryBuilder.cleanRawQuery(" test "));
    assertEquals("test \\\"query\\\"", ElasticsearchQueryBuilder.cleanRawQuery("test \"query\""));
  }

  @Test
  void testGetPropertiesFacetField() {
    // Core bool fields
    assertEquals("isBIE", ElasticsearchQueryBuilder.getPropertiesFacetField("isBIE"));

    // Core fields
    assertEquals(
        "scientificName.keyword",
        ElasticsearchQueryBuilder.getPropertiesFacetField("scientificName"));

    // Classification fields
    assertEquals(
        "classification.kingdom.keyword",
        ElasticsearchQueryBuilder.getPropertiesFacetField("classification.kingdom"));

    // Other (properties)
    assertEquals(
        "properties.customField.keyword",
        ElasticsearchQueryBuilder.getPropertiesFacetField("customField"));
  }

  @Test
  void testBuildQueryBasic() {
    BoolQuery.Builder bq = new BoolQuery.Builder();
    ElasticsearchQueryBuilder.buildQuery(
        "macropus", "list123", null, false, false, Collections.emptyList(), bq);

    BoolQuery query = bq.build();
    assertNotNull(query);
    // The builder modifies the bool query inline. We can assert it built successfully.
    // Deep introspection of the BoolQuery object can be complex with the new Java API Client,
    // so we mainly verify no exceptions are thrown and structure is built.
    assertFalse(query.filter().isEmpty(), "Should have filters for listId and privacy");
  }

  @Test
  void testBuildQueryWithMultipleListIds() {
    BoolQuery.Builder bq = new BoolQuery.Builder();
    List<FieldValue> listIds = Arrays.asList(FieldValue.of("list1"), FieldValue.of("list2"));

    ElasticsearchQueryBuilder.buildQuery(
        null, listIds, "user1", true, null, Collections.emptyList(), bq);

    BoolQuery query = bq.build();
    assertNotNull(query);
  }

  @Test
  void testBuildListSearchQuery() {
    BoolQuery.Builder bq = new BoolQuery.Builder();

    ElasticsearchQueryBuilder.buildListSearchQuery(
        "birds", "user1", false, true, Collections.emptyList(), bq);

    BoolQuery query = bq.build();
    assertNotNull(query);
  }

  @Test
  void testAddFiltersStandard() {
    BoolQuery.Builder bq = new BoolQuery.Builder();
    Filter f1 = new Filter();
    f1.setKey("kingdom");
    f1.setValue("Animalia");

    ElasticsearchQueryBuilder.buildQuery(
        null, "list123", null, false, false, Collections.singletonList(f1), bq);

    BoolQuery query = bq.build();
    assertNotNull(query);
  }

  @Test
  void testAddFiltersProperties() {
    BoolQuery.Builder bq = new BoolQuery.Builder();
    Filter f1 = new Filter();
    f1.setKey("properties.habitat");
    f1.setValue("marine");

    ElasticsearchQueryBuilder.buildQuery(
        null, "list123", null, false, false, Collections.singletonList(f1), bq);

    BoolQuery query = bq.build();
    assertNotNull(query);
  }

  @Test
  void testRestrictFields() {
    BoolQuery.Builder bq = new BoolQuery.Builder();
    HashSet<String> fields = new HashSet<>(Arrays.asList("scientificName", "habitat"));

    ElasticsearchQueryBuilder.restrictFields("kangaroo", fields, bq);

    BoolQuery query = bq.build();
    assertNotNull(query);
  }
}
