package au.org.ala.listsapi.controller;

import static au.org.ala.listsapi.util.ElasticsearchQueryBuilder.buildQuery;
import static org.junit.jupiter.api.Assertions.*;

import au.org.ala.listsapi.model.Filter;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
// Assuming you use Apache Commons Lang
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Assume your class containing buildQuery is named QueryBuilderUtil
// import static your.package.QueryBuilderUtil.buildQuery;

// --- Mock Filter class (if not already defined) ---
// (Add the Filter class definition from above here if needed)
// --- End Mock Filter class ---

class QueryBuilderUtilTest {

    private BoolQuery.Builder bq;
    private static final Logger logger = LoggerFactory.getLogger(QueryBuilderUtilTest.class);

    @BeforeEach
    void setUp() {
        // Start with a fresh builder for each test
        bq = new BoolQuery.Builder();
    }

    @Test
    void testBuildQuery_withSearchQuery() {
        String searchQuery = "Test Query";
        buildQuery(searchQuery, "", null, null, null, null, bq);
        BoolQuery query = bq.build();
        logger.info("filters = {}", query.filter());

        assertNotNull(query.should());
        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrase());
        assertEquals("all", query.should().get(0).matchPhrase().field());
        assertEquals("test query*", query.should().get(0).matchPhrase().query());
        assertEquals(2.0f, query.should().get(0).matchPhrase().boost());
        assertEquals("1", query.minimumShouldMatch()); // Because length > 1
        assertEquals(1, query.filter().size());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withShortSearchQuery() {
        String searchQuery = "T"; // Length is 1
        buildQuery(searchQuery, "", null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertNotNull(query.should());
        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrase());
        assertEquals("t*", query.should().get(0).matchPhrase().query());
        assertNull(query.minimumShouldMatch()); // Because length <= 1
        assertEquals(1, query.filter().size());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withEmptySearchQuery() {
        String searchQuery = "";
        buildQuery(searchQuery, "", null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.filter().size());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withNullSearchQuery() {
        buildQuery(null, "", null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.filter().size());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withUserId() {
        String userId = "user123";
        buildQuery(null, "", userId, null, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertNotNull(query.filter());
        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("owner", query.filter().get(0).term().field());
        assertEquals(FieldValue.of(userId), query.filter().get(0).term().value());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withIsPrivateTrue() {
        buildQuery("", "", null, true, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertNotNull(query.filter());
        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertEquals(FieldValue.of(true), query.filter().get(0).term().value());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withIsPrivateFalse() {
        buildQuery("", "", null, false, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertNotNull(query.filter());
        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertEquals(FieldValue.of(false), query.filter().get(0).term().value());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withSpeciesListId() {
        String speciesListId = "list-abc";
        buildQuery(null, speciesListId, null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertNotNull(query.filter());
        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("speciesListID", query.filter().get(0).term().field());
        assertEquals(FieldValue.of(speciesListId), query.filter().get(0).term().value());
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withSingleFilter() {
        List<Filter> filters = Collections.singletonList(new Filter("attr1", "value1"));
        buildQuery(null, "", null, null, null, filters, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertTrue(query.filter().isEmpty());
        assertNotNull(query.must());
        assertEquals(1, query.must().size());

        // Must clause contains a bool query
        assertTrue(query.must().get(0).isBool());
        BoolQuery innerBool = query.must().get(0).bool();

        // Inner bool query has one must clause which is a term query
        assertTrue(innerBool.should().isEmpty()); // No should in inner bool
        assertNull(innerBool.minimumShouldMatch());
        assertTrue(innerBool.filter().isEmpty());
        assertNotNull(innerBool.must());
        assertEquals(1, innerBool.must().size());
        assertTrue(innerBool.must().get(0).isTerm());
        assertEquals("attr1", innerBool.must().get(0).term().field());
        assertEquals(FieldValue.of("value1"), innerBool.must().get(0).term().value());
    }

    @Test
    void testBuildQuery_withMultipleFilters_DifferentKeys() {
        List<Filter> filters =
                Arrays.asList(
                        //        new Filter("attr1", "value1"),
                        //        new Filter("attr2", "value2")
                        );
        BoolQuery query = buildQuery("", "", null, null, null, filters, bq).build();
        //    BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertTrue(query.filter().isEmpty());
        assertNotNull(query.must());
        assertEquals(2, query.must().size()); // One must clause per key

        // Check first must clause (for attr1)
        assertTrue(query.must().get(0).isBool());
        BoolQuery innerBool1 = query.must().get(0).bool();
        assertEquals(1, innerBool1.must().size());
        assertTrue(innerBool1.must().get(0).isTerm());
        assertEquals("attr1", innerBool1.must().get(0).term().field());
        assertEquals(FieldValue.of("value1"), innerBool1.must().get(0).term().value());
        assertTrue(innerBool1.should().isEmpty());
        assertNull(innerBool1.minimumShouldMatch());

        // Check second must clause (for attr2)
        assertTrue(query.must().get(1).isBool());
        BoolQuery innerBool2 = query.must().get(1).bool();
        assertEquals(1, innerBool2.must().size());
        assertTrue(innerBool2.must().get(0).isTerm());
        assertEquals("attr2", innerBool2.must().get(0).term().field());
        assertEquals(FieldValue.of("value2"), innerBool2.must().get(0).term().value());
        assertTrue(innerBool2.should().isEmpty());
        assertNull(innerBool2.minimumShouldMatch());
    }

    @Test
    void testBuildQuery_withMultipleFilters_SameKey() {
        List<Filter> filters =
                Arrays.asList(new Filter("attr1", "value1"), new Filter("attr1", "value2"));
        buildQuery("", "", null, null, null, filters, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.filter().size());
        assertNotNull(query.must());
        assertEquals(1, query.must().size()); // Only one must clause for the key "attr1"

        // Must clause contains a bool query
        assertTrue(query.must().get(0).isBool());
        BoolQuery innerBool = query.must().get(0).bool();

        // Inner bool query has two should clauses (OR logic)
        assertTrue(innerBool.must().isEmpty()); // No must in inner bool
        assertTrue(innerBool.filter().isEmpty());
        assertNotNull(innerBool.should());
        assertEquals(2, innerBool.should().size());
        assertEquals("1", innerBool.minimumShouldMatch()); // OR requires minimum 1 match

        // Check first should clause
        assertTrue(innerBool.should().get(0).isTerm());
        assertEquals("attr1", innerBool.should().get(0).term().field());
        assertEquals("value1", innerBool.should().get(0).term().value().stringValue());

        // Check second should clause
        assertTrue(innerBool.should().get(1).isTerm());
        assertEquals("attr1", innerBool.should().get(1).term().field());
        assertEquals("value2", innerBool.should().get(1).term().value().stringValue());
    }

    @Test
    void testBuildQuery_withMixedFilters() {
        List<Filter> filters =
                Arrays.asList(
                        new Filter("attr1", "value1"),
                        new Filter("attr2", "valueA"),
                        new Filter("attr1", "value2"),
                        new Filter("attr3", "valueX"));
        buildQuery(null, "", null, null, null, filters, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertNull(query.minimumShouldMatch());
        assertTrue(query.filter().isEmpty());
        assertNotNull(query.must());
        assertEquals(3, query.must().size()); // One must clause per key (attr1, attr2, attr3)

        // Find and verify the bool query for attr1 (which should have OR logic)
        Query attr1Query =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().should().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals("attr1")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr1Query);
        BoolQuery innerBoolAttr1 = attr1Query.bool();
        assertEquals(2, innerBoolAttr1.should().size());
        assertEquals("1", innerBoolAttr1.minimumShouldMatch());
        assertTrue(innerBoolAttr1.must().isEmpty());
        assertTrue(
                innerBoolAttr1.should().stream()
                        .anyMatch(t -> t.term().value().equals(FieldValue.of("value1"))));
        assertTrue(
                innerBoolAttr1.should().stream()
                        .anyMatch(t -> t.term().value().equals(FieldValue.of("value2"))));

        // Find and verify the bool query for attr2 (single term)
        Query attr2Query =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals("attr2")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr2Query);
        BoolQuery innerBoolAttr2 = attr2Query.bool();
        assertEquals(1, innerBoolAttr2.must().size());
        assertTrue(innerBoolAttr2.must().get(0).isTerm());
        assertEquals("attr2", innerBoolAttr2.must().get(0).term().field());
        assertEquals(FieldValue.of("valueA"), innerBoolAttr2.must().get(0).term().value());
        assertTrue(innerBoolAttr2.should().isEmpty());
        assertNull(innerBoolAttr2.minimumShouldMatch());

        // Find and verify the bool query for attr3 (single term)
        Query attr3Query =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals("attr3")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr3Query);
        BoolQuery innerBoolAttr3 = attr3Query.bool();
        assertEquals(1, innerBoolAttr3.must().size());
        assertTrue(innerBoolAttr3.must().get(0).isTerm());
        assertEquals("attr3", innerBoolAttr3.must().get(0).term().field());
        assertEquals(FieldValue.of("valueX"), innerBoolAttr3.must().get(0).term().value());
        assertTrue(innerBoolAttr3.should().isEmpty());
        assertNull(innerBoolAttr3.minimumShouldMatch());
    }

    @Test
    void testBuildQuery_withNullFilters() {
        buildQuery("search", "list1", "user1", true, null, null, bq);
        BoolQuery query = bq.build();

        // Check other clauses are present
        assertEquals(1, query.should().size());
        assertEquals("1", query.minimumShouldMatch());
        assertEquals(3, query.filter().size());
        // Must clause should be empty as no list filters were added
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withEmptyFilters() {
        buildQuery("search", "list1", "user1", false, false, new ArrayList<Filter>(), bq);
        BoolQuery query = bq.build();

        // Check other clauses are present
        assertEquals(1, query.should().size());
        assertEquals("1", query.minimumShouldMatch());
        assertEquals(3, query.filter().size());
        // Must clause should be empty as no list filters were added
        assertTrue(query.must().isEmpty());
    }

    @Test
    void testBuildQuery_withAllParameters() {
        String searchQuery = "Complex Search";
        String speciesListId = "list-xyz";
        String userId = "user999";
        boolean isPrivate = false;
        List<Filter> filters =
                Arrays.asList(
                        new Filter("tag", "important"),
                        new Filter("status", "active"),
                        new Filter("tag", "urgent"));

        buildQuery(searchQuery, speciesListId, userId, false, isPrivate, filters, bq);
        BoolQuery query = bq.build();

        // Check search query part
        assertNotNull(query.should());
        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrase());
        assertEquals("complex search*", query.should().get(0).matchPhrase().query());
        assertEquals("1", query.minimumShouldMatch());

        // Check filter part
        assertNotNull(query.filter());
        assertEquals(3, query.filter().size()); // owner, isPrivate, speciesListID
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.term().field().equals("owner")
                                                && f.term().value().equals(FieldValue.of(userId))));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.term().field().equals("isPrivate")
                                                && f.term()
                                                        .value()
                                                        .equals(FieldValue.of(isPrivate))));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.term().field().equals("speciesListID")
                                                && f.term()
                                                        .value()
                                                        .equals(FieldValue.of(speciesListId))));

        // Check must part (from filters list)
        assertNotNull(query.must());
        assertEquals(2, query.must().size()); // One for "tag", one for "status"

        // Check "tag" must clause (should have OR logic)
        Query tagQuery =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().should().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals("tag")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(tagQuery);
        BoolQuery innerBoolTag = tagQuery.bool();
        assertEquals(2, innerBoolTag.should().size());
        assertEquals("1", innerBoolTag.minimumShouldMatch());
        assertTrue(innerBoolTag.must().isEmpty());
        assertTrue(
                innerBoolTag.should().stream()
                        .anyMatch(t -> t.term().value().equals(FieldValue.of("important"))));
        assertTrue(
                innerBoolTag.should().stream()
                        .anyMatch(t -> t.term().value().equals(FieldValue.of("urgent"))));

        // Check "status" must clause (single term)
        Query statusQuery =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals("status")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(statusQuery);
        BoolQuery innerBoolStatus = statusQuery.bool();
        assertEquals(1, innerBoolStatus.must().size());
        assertTrue(innerBoolStatus.must().get(0).isTerm());
        assertEquals("status", innerBoolStatus.must().get(0).term().field());
        assertEquals(FieldValue.of("active"), innerBoolStatus.must().get(0).term().value());
        assertTrue(innerBoolStatus.should().isEmpty());
        assertNull(innerBoolStatus.minimumShouldMatch());
    }

    @Test
    void testBuildQuery_withExistingBuilder() {
        // Pre-populate the builder
        bq.must(m -> m.term(t -> t.field("initial_must").value("value")));
        bq.filter(f -> f.term(t -> t.field("initial_filter").value("value")));

        String userId = "user123";
        buildQuery(null, "", userId, null, null, null, bq); // Add only userId filter
        BoolQuery query = bq.build();

        // Verify existing clauses are still there
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isTerm());
        assertEquals("initial_must", query.must().get(0).term().field());

        assertEquals(2, query.filter().size()); // initial_filter + owner filter
        assertTrue(
                query.filter().stream().anyMatch(f -> f.term().field().equals("initial_filter")));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.term().field().equals("owner")
                                                && f.term().value().equals(FieldValue.of(userId))));

        // Verify no should clauses were added
        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
    }
}
