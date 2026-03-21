package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.*;

import au.org.ala.listsapi.model.Filter;
import au.org.ala.listsapi.util.ElasticsearchQueryBuilder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class QueryBuilderUtilTest {

    private BoolQuery.Builder bq;
    private static final Logger logger = LoggerFactory.getLogger(QueryBuilderUtilTest.class);

    @BeforeEach
    void setUp() {
        bq = new BoolQuery.Builder();
    }

    /**
     * Non-blank searchQuery → matchPhrasePrefix in should, minimumShouldMatch="1". isAdmin=null
     * treated as non-admin: userId=null + non-admin → isPrivate=false filter. speciesListID=null →
     * no speciesListID filter.
     */
    @Test
    void testBuildQuery_withSearchQuery() {
        String searchQuery = "Test Query";
        ElasticsearchQueryBuilder.buildQuery(
                searchQuery, (String) null, null, null, null, null, bq);
        BoolQuery query = bq.build();
        logger.info("filters = {}", query.filter());

        assertNotNull(query.should());
        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrasePrefix());
        assertEquals("all", query.should().get(0).matchPhrasePrefix().field());
        assertEquals("test query", query.should().get(0).matchPhrasePrefix().query());
        assertEquals(2.0f, query.should().get(0).matchPhrasePrefix().boost());
        assertEquals("1", query.minimumShouldMatch());
        assertTrue(query.must().isEmpty());

        // isPrivate=false filter (null userId + non-admin), no speciesListID filter
        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertFalse(query.filter().get(0).term().value().booleanValue());
    }

    /**
     * Short searchQuery (length ≤ 1) → matchPhrasePrefix in should, no minimumShouldMatch.
     * isAdmin=null treated as non-admin: userId=null + non-admin → isPrivate=false filter.
     */
    @Test
    void testBuildQuery_withShortSearchQuery() {
        String searchQuery = "T";
        ElasticsearchQueryBuilder.buildQuery(
                searchQuery, (String) null, null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertNotNull(query.should());
        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrasePrefix());
        assertEquals("all", query.should().get(0).matchPhrasePrefix().field());
        assertEquals("t", query.should().get(0).matchPhrasePrefix().query());
        assertNull(query.minimumShouldMatch());
        assertTrue(query.must().isEmpty());

        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertFalse(query.filter().get(0).term().value().booleanValue());
    }

    /**
     * Blank searchQuery → matchAll in must (not should). No minimumShouldMatch. isAdmin=null
     * treated as non-admin: userId=null + non-admin → isPrivate=false filter.
     */
    @Test
    void testBuildQuery_withEmptySearchQuery() {
        ElasticsearchQueryBuilder.buildQuery("", (String) null, null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isMatchAll());

        assertEquals(1, query.filter().size());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertFalse(query.filter().get(0).term().value().booleanValue());
    }

    /** Null searchQuery behaves identically to blank: matchAll in must. */
    @Test
    void testBuildQuery_withNullSearchQuery() {
        ElasticsearchQueryBuilder.buildQuery(null, (String) null, null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isMatchAll());

        assertEquals(1, query.filter().size());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertFalse(query.filter().get(0).term().value().booleanValue());
    }

    /**
     * Non-null userId → owner filter added; no isPrivate filter (userId!=null, isPrivate=null).
     * Null searchQuery → matchAll in must.
     */
    @Test
    void testBuildQuery_withUserId() {
        String userId = "user123";
        ElasticsearchQueryBuilder.buildQuery(null, (String) null, userId, null, null, null, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isMatchAll());

        // owner filter only (no isPrivate because userId!=null, no speciesListID)
        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("owner", query.filter().get(0).term().field());
        assertEquals(userId, query.filter().get(0).term().value().stringValue());
    }

    /**
     * isAdmin=true, isPrivate=true → isPrivate=true filter (via else-if branch). Blank search →
     * matchAll in must. No owner filter (userId=null, isAdmin=true).
     */
    @Test
    void testBuildQuery_withIsPrivateTrue() {
        ElasticsearchQueryBuilder.buildQuery("", (String) null, null, true, true, null, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isMatchAll());

        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertTrue(query.filter().get(0).term().value().booleanValue());
    }

    /**
     * isAdmin=false (explicit), userId=null → non-admin path: isPrivate=false filter added. Blank
     * search → matchAll in must.
     */
    @Test
    void testBuildQuery_withIsPrivateFalse() {
        ElasticsearchQueryBuilder.buildQuery("", (String) null, null, false, null, null, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isMatchAll());

        assertEquals(1, query.filter().size());
        assertTrue(query.filter().get(0).isTerm());
        assertEquals("isPrivate", query.filter().get(0).term().field());
        assertFalse(query.filter().get(0).term().value().booleanValue());
    }

    /**
     * Non-null speciesListID → speciesListID filter added. Null userId + non-admin →
     * isPrivate=false filter.
     */
    @Test
    void testBuildQuery_withSpeciesListId() {
        String speciesListId = "list-abc";
        ElasticsearchQueryBuilder.buildQuery(null, speciesListId, null, null, null, null, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
        assertEquals(1, query.must().size());
        assertTrue(query.must().get(0).isMatchAll());

        assertEquals(2, query.filter().size());
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "speciesListID".equals(f.term().field())
                                                && speciesListId.equals(
                                                        f.term().value().stringValue())));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "isPrivate".equals(f.term().field())
                                                && !f.term().value().booleanValue()));
    }

    /**
     * Single filter with a non-core key → standard filter → must clause with inner bool having one
     * must term. Null searchQuery → matchAll in must. So total must = 2 (matchAll + filter clause).
     */
    @Test
    void testBuildQuery_withSingleFilter() {
        List<Filter> filters = Collections.singletonList(new Filter("attr1", "value1"));
        ElasticsearchQueryBuilder.buildQuery(null, (String) null, null, null, null, filters, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());

        // isPrivate=false filter only (null userId + non-admin, no speciesListID)
        assertEquals(1, query.filter().size());

        // matchAll + one filter-derived must clause
        assertNotNull(query.must());
        assertEquals(2, query.must().size());

        // Find the filter-derived must clause (a bool query, not matchAll)
        Query filterMust =
                query.must().stream().filter(q -> !q.isMatchAll()).findFirst().orElse(null);
        assertNotNull(filterMust);
        assertTrue(filterMust.isBool());
        BoolQuery innerBool = filterMust.bool();

        assertTrue(innerBool.should().isEmpty());
        assertNull(innerBool.minimumShouldMatch());
        assertTrue(innerBool.filter().isEmpty());
        assertEquals(1, innerBool.must().size());
        assertTrue(innerBool.must().get(0).isTerm());
        // attr1 is not a core field → mapped to "properties.attr1.keyword"
        assertEquals("properties.attr1.keyword", innerBool.must().get(0).term().field());
        assertEquals("value1", innerBool.must().get(0).term().value().stringValue());
    }

    /**
     * Two filters with different keys → two filter-derived must clauses. Blank search → matchAll in
     * must. Total must = 3 (matchAll + 2 filter clauses).
     */
    @Test
    void testBuildQuery_withMultipleFilters_DifferentKeys() {
        List<Filter> filters =
                Arrays.asList(new Filter("attr1", "value1"), new Filter("attr2", "value2"));
        BoolQuery query =
                ElasticsearchQueryBuilder.buildQuery(
                                "", (String) null, null, null, null, filters, bq)
                        .build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());

        // isPrivate=false filter (null userId + non-admin)
        assertEquals(1, query.filter().size());

        // matchAll + 2 filter-derived must clauses
        assertNotNull(query.must());
        assertEquals(3, query.must().size());

        // Find must clause for attr1
        Query attr1Clause =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals(
                                                                                        "properties.attr1.keyword")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr1Clause);
        BoolQuery innerBool1 = attr1Clause.bool();
        assertEquals(1, innerBool1.must().size());
        assertTrue(innerBool1.must().get(0).isTerm());
        assertEquals("properties.attr1.keyword", innerBool1.must().get(0).term().field());
        assertEquals("value1", innerBool1.must().get(0).term().value().stringValue());
        assertTrue(innerBool1.should().isEmpty());
        assertNull(innerBool1.minimumShouldMatch());

        // Find must clause for attr2
        Query attr2Clause =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        t.term()
                                                                                .field()
                                                                                .equals(
                                                                                        "properties.attr2.keyword")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr2Clause);
        BoolQuery innerBool2 = attr2Clause.bool();
        assertEquals(1, innerBool2.must().size());
        assertTrue(innerBool2.must().get(0).isTerm());
        assertEquals("properties.attr2.keyword", innerBool2.must().get(0).term().field());
        assertEquals("value2", innerBool2.must().get(0).term().value().stringValue());
        assertTrue(innerBool2.should().isEmpty());
        assertNull(innerBool2.minimumShouldMatch());
    }

    /**
     * Two filters with the same key → one filter-derived must clause with inner bool using should
     * (OR logic). Blank search → matchAll in must. Total must = 2 (matchAll + same-key clause).
     */
    @Test
    void testBuildQuery_withMultipleFilters_SameKey() {
        List<Filter> filters =
                Arrays.asList(new Filter("attr1", "value1"), new Filter("attr1", "value2"));
        ElasticsearchQueryBuilder.buildQuery("", (String) null, null, null, null, filters, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());

        // isPrivate=false filter
        assertEquals(1, query.filter().size());

        // matchAll + one filter-derived must clause
        assertNotNull(query.must());
        assertEquals(2, query.must().size());

        // Find the filter-derived must clause
        Query filterMust =
                query.must().stream().filter(q -> !q.isMatchAll()).findFirst().orElse(null);
        assertNotNull(filterMust);
        assertTrue(filterMust.isBool());
        BoolQuery innerBool = filterMust.bool();

        assertTrue(innerBool.must().isEmpty());
        assertTrue(innerBool.filter().isEmpty());
        assertNotNull(innerBool.should());
        assertEquals(2, innerBool.should().size());
        assertEquals("1", innerBool.minimumShouldMatch());

        assertTrue(innerBool.should().get(0).isTerm());
        assertEquals("properties.attr1.keyword", innerBool.should().get(0).term().field());
        assertEquals("value1", innerBool.should().get(0).term().value().stringValue());

        assertTrue(innerBool.should().get(1).isTerm());
        assertEquals("properties.attr1.keyword", innerBool.should().get(1).term().field());
        assertEquals("value2", innerBool.should().get(1).term().value().stringValue());
    }

    /**
     * Mixed filters: attr1 (x2, same key → OR), attr2 (x1), attr3 (x1). Null search → matchAll in
     * must. Total must = 4 (matchAll + 3 filter clauses).
     */
    @Test
    void testBuildQuery_withMixedFilters() {
        List<Filter> filters =
                Arrays.asList(
                        new Filter("attr1", "value1"),
                        new Filter("attr2", "valueA"),
                        new Filter("attr1", "value2"),
                        new Filter("attr3", "valueX"));
        ElasticsearchQueryBuilder.buildQuery(null, (String) null, null, null, null, filters, bq);
        BoolQuery query = bq.build();

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());

        // isPrivate=false filter
        assertEquals(1, query.filter().size());

        // matchAll + 3 filter-derived must clauses
        assertNotNull(query.must());
        assertEquals(4, query.must().size());

        // Find and verify attr1 (OR logic)
        Query attr1Query =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().should().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        "properties.attr1.keyword"
                                                                                .equals(
                                                                                        t.term()
                                                                                                .field())))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr1Query);
        BoolQuery innerBoolAttr1 = attr1Query.bool();
        assertEquals(2, innerBoolAttr1.should().size());
        assertEquals("1", innerBoolAttr1.minimumShouldMatch());
        assertTrue(innerBoolAttr1.must().isEmpty());
        assertTrue(
                innerBoolAttr1.should().stream()
                        .anyMatch(t -> "value1".equals(t.term().value().stringValue())));
        assertTrue(
                innerBoolAttr1.should().stream()
                        .anyMatch(t -> "value2".equals(t.term().value().stringValue())));

        // Find and verify attr2 (single term)
        Query attr2Query =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        "properties.attr2.keyword"
                                                                                .equals(
                                                                                        t.term()
                                                                                                .field())))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr2Query);
        BoolQuery innerBoolAttr2 = attr2Query.bool();
        assertEquals(1, innerBoolAttr2.must().size());
        assertTrue(innerBoolAttr2.must().get(0).isTerm());
        assertEquals("properties.attr2.keyword", innerBoolAttr2.must().get(0).term().field());
        assertEquals("valueA", innerBoolAttr2.must().get(0).term().value().stringValue());
        assertTrue(innerBoolAttr2.should().isEmpty());
        assertNull(innerBoolAttr2.minimumShouldMatch());

        // Find and verify attr3 (single term)
        Query attr3Query =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        "properties.attr3.keyword"
                                                                                .equals(
                                                                                        t.term()
                                                                                                .field())))
                        .findFirst()
                        .orElse(null);
        assertNotNull(attr3Query);
        BoolQuery innerBoolAttr3 = attr3Query.bool();
        assertEquals(1, innerBoolAttr3.must().size());
        assertTrue(innerBoolAttr3.must().get(0).isTerm());
        assertEquals("properties.attr3.keyword", innerBoolAttr3.must().get(0).term().field());
        assertEquals("valueX", innerBoolAttr3.must().get(0).term().value().stringValue());
        assertTrue(innerBoolAttr3.should().isEmpty());
        assertNull(innerBoolAttr3.minimumShouldMatch());
    }

    /**
     * Null filters → no filter-derived must clauses. userId="user1" + isAdmin=true +
     * speciesListID="list1" → owner filter + speciesListID filter (isPrivate=null → no isPrivate
     * filter). Non-blank search → matchPhrasePrefix in should.
     */
    @Test
    void testBuildQuery_withNullFilters() {
        ElasticsearchQueryBuilder.buildQuery("search", "list1", "user1", true, null, null, bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrasePrefix());
        assertEquals("1", query.minimumShouldMatch());

        // owner (userId!=null) + speciesListID (no isPrivate because userId!=null, isPrivate=null)
        assertEquals(2, query.filter().size());
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "owner".equals(f.term().field())
                                                && "user1".equals(f.term().value().stringValue())));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "speciesListID".equals(f.term().field())
                                                && "list1".equals(f.term().value().stringValue())));

        assertTrue(query.must().isEmpty());
    }

    /**
     * Empty filters list → no filter-derived must clauses. userId="user1" + isAdmin=false +
     * isPrivate=false → owner + isPrivate=false + speciesListID filters.
     */
    @Test
    void testBuildQuery_withEmptyFilters() {
        ElasticsearchQueryBuilder.buildQuery(
                "search", "list1", "user1", false, false, new ArrayList<Filter>(), bq);
        BoolQuery query = bq.build();

        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrasePrefix());
        assertEquals("1", query.minimumShouldMatch());

        // owner + isPrivate=false (userId!=null, isPrivate!=null) + speciesListID
        assertEquals(3, query.filter().size());
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "owner".equals(f.term().field())
                                                && "user1".equals(f.term().value().stringValue())));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "isPrivate".equals(f.term().field())
                                                && !f.term().value().booleanValue()));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "speciesListID".equals(f.term().field())
                                                && "list1".equals(f.term().value().stringValue())));

        assertTrue(query.must().isEmpty());
    }

    /**
     * Full scenario: searchQuery + speciesListID + userId + isAdmin=false + isPrivate=false + mixed
     * filters (tag x2, status x1).
     */
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

        ElasticsearchQueryBuilder.buildQuery(
                searchQuery, speciesListId, userId, false, isPrivate, filters, bq);
        BoolQuery query = bq.build();

        // matchPhrasePrefix in should (length > 1)
        assertEquals(1, query.should().size());
        assertTrue(query.should().get(0).isMatchPhrasePrefix());
        assertEquals("all", query.should().get(0).matchPhrasePrefix().field());
        assertEquals("complex search", query.should().get(0).matchPhrasePrefix().query());
        assertEquals("1", query.minimumShouldMatch());

        // owner + isPrivate=false (userId!=null, isPrivate!=null) + speciesListID
        assertEquals(3, query.filter().size());
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "owner".equals(f.term().field())
                                                && userId.equals(f.term().value().stringValue())));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "isPrivate".equals(f.term().field())
                                                && !f.term().value().booleanValue()));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "speciesListID".equals(f.term().field())
                                                && speciesListId.equals(
                                                        f.term().value().stringValue())));

        // Two filter-derived must clauses: "tag" (OR) and "status" (single)
        assertEquals(2, query.must().size());

        // "tag" must clause (OR logic)
        Query tagQuery =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().should().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        "properties.tag.keyword"
                                                                                .equals(
                                                                                        t.term()
                                                                                                .field())))
                        .findFirst()
                        .orElse(null);
        assertNotNull(tagQuery);
        BoolQuery innerBoolTag = tagQuery.bool();
        assertEquals(2, innerBoolTag.should().size());
        assertEquals("1", innerBoolTag.minimumShouldMatch());
        assertTrue(innerBoolTag.must().isEmpty());
        assertTrue(
                innerBoolTag.should().stream()
                        .anyMatch(t -> "important".equals(t.term().value().stringValue())));
        assertTrue(
                innerBoolTag.should().stream()
                        .anyMatch(t -> "urgent".equals(t.term().value().stringValue())));

        // "status" must clause (single term)
        Query statusQuery =
                query.must().stream()
                        .filter(
                                q ->
                                        q.isBool()
                                                && q.bool().must().stream()
                                                        .anyMatch(
                                                                t ->
                                                                        "properties.status.keyword"
                                                                                .equals(
                                                                                        t.term()
                                                                                                .field())))
                        .findFirst()
                        .orElse(null);
        assertNotNull(statusQuery);
        BoolQuery innerBoolStatus = statusQuery.bool();
        assertEquals(1, innerBoolStatus.must().size());
        assertTrue(innerBoolStatus.must().get(0).isTerm());
        assertEquals("properties.status.keyword", innerBoolStatus.must().get(0).term().field());
        assertEquals("active", innerBoolStatus.must().get(0).term().value().stringValue());
        assertTrue(innerBoolStatus.should().isEmpty());
        assertNull(innerBoolStatus.minimumShouldMatch());
    }

    /**
     * Pre-populated builder: existing clauses are preserved. buildQuery(null, "", userId, ...) adds
     * matchAll to must + owner filter + speciesListID="" filter (non-null ""). Total must = 2
     * (initial term + matchAll), filter = 3 (initial + owner + speciesListID="").
     */
    @Test
    void testBuildQuery_withExistingBuilder() {
        bq.must(m -> m.term(t -> t.field("initial_must").value("value")));
        bq.filter(f -> f.term(t -> t.field("initial_filter").value("value")));

        String userId = "user123";
        // speciesListID="" is non-null → adds speciesListID filter
        ElasticsearchQueryBuilder.buildQuery(null, "", userId, null, null, null, bq);
        BoolQuery query = bq.build();

        // initial term + matchAll
        assertEquals(2, query.must().size());
        assertTrue(
                query.must().stream()
                        .anyMatch(m -> m.isTerm() && "initial_must".equals(m.term().field())));
        assertTrue(query.must().stream().anyMatch(Query::isMatchAll));

        // initial_filter + owner + speciesListID=""
        assertEquals(3, query.filter().size());
        assertTrue(
                query.filter().stream()
                        .anyMatch(f -> f.isTerm() && "initial_filter".equals(f.term().field())));
        assertTrue(
                query.filter().stream()
                        .anyMatch(
                                f ->
                                        f.isTerm()
                                                && "owner".equals(f.term().field())
                                                && userId.equals(f.term().value().stringValue())));

        assertTrue(query.should().isEmpty());
        assertNull(query.minimumShouldMatch());
    }
}
