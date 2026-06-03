package au.org.ala.listsapi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import au.org.ala.listsapi.model.Classification;
import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.KvpValueVersion1;
import au.org.ala.listsapi.model.QueryListItemVersion1;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.UserdetailsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SpeciesListTransformerTest {

  private SpeciesListTransformer transformer;
  private UserdetailsService userdetailsService;
  private SpeciesListMongoRepository speciesListMongoRepository;

  @BeforeEach
  void setUp() {
    userdetailsService = mock(UserdetailsService.class);
    speciesListMongoRepository = mock(SpeciesListMongoRepository.class);
    transformer = new SpeciesListTransformer(userdetailsService, speciesListMongoRepository);
    ReflectionTestUtils.setField(transformer, "legacyLookupUsersEnabled", false);
  }

  @Test
  void testTransformToVersion1_SdsTrue() {
    SpeciesList speciesList = new SpeciesList();
    speciesList.setIsSDS(true);
    speciesList.setTitle("Test List");

    SpeciesListVersion1 result = transformer.transformToVersion1(speciesList);

    assertEquals("CONSERVATION", result.getSdsType());
    assertEquals(Boolean.TRUE, result.getIsSDS());
  }

  @Test
  void testTransformToVersion1_SdsFalse() {
    SpeciesList speciesList = new SpeciesList();
    speciesList.setIsSDS(false);
    speciesList.setTitle("Test List");

    SpeciesListVersion1 result = transformer.transformToVersion1(speciesList);

    assertEquals("", result.getSdsType());
    assertEquals(Boolean.FALSE, result.getIsSDS());
  }

  @Test
  void testTransformToVersion1_SdsNull() {
    SpeciesList speciesList = new SpeciesList();
    speciesList.setIsSDS(null);
    speciesList.setTitle("Test List");

    SpeciesListVersion1 result = transformer.transformToVersion1(speciesList);

    assertEquals("", result.getSdsType());
    assertNull(result.getIsSDS());
  }

  @Test
  void testTransformToVersion1_Authority() {
    SpeciesList speciesList = new SpeciesList();
    speciesList.setAuthority("Test Authority");
    speciesList.setTitle("Test List");

    SpeciesListVersion1 result = transformer.transformToVersion1(speciesList);

    assertEquals("Test Authority", result.getAuthority());
  }

  @Test
  void testTransformToVersion1_EmptyDefaults() {
    SpeciesList speciesList = new SpeciesList();
    speciesList.setTitle("Test List");
    // Ensure these fields are null
    speciesList.setCategory(null);
    speciesList.setAuthority(null);
    speciesList.setIsSDS(null);
    // Generalisation is not in SpeciesList model but is in Version1

    SpeciesListVersion1 result = transformer.transformToVersion1(speciesList);

    assertEquals("", result.getCategory());
    assertEquals("", result.getAuthority());
    assertEquals("", result.getSdsType());
    assertEquals("", result.getGeneralisation());
  }
  
  @Nested
  @DisplayName("Legacy Keys Transformation Tests")
  class TransformLegacyKeysTests {
    @Test
    @DisplayName("Should apply legacy key transformations along with duplication")
    void shouldApplyLegacyKeyTransformations() {
      List<String> keys = Arrays.asList("rawfamily", "taxonRank", "CommonNames", "group");
      Set<String> result = SpeciesListTransformer.transformLegacyKeys(keys);
      
      assertEquals(8, result.size());
      assertTrue(result.contains("rawfamily"));
      assertTrue(result.contains("family"));
      assertTrue(result.contains("taxonRank"));
      assertTrue(result.contains("rank"));
      assertTrue(result.contains("CommonNames"));
      assertTrue(result.contains("common name"));
      assertTrue(result.contains("group"));
      assertTrue(result.contains("Group"));
    }

    @Test
    @DisplayName("Should add space-separated duplicate for keys with underscores")
    void shouldDuplicateKeysWithUnderscores() {
      List<String> keys = Arrays.asList("custom_field_name", "Another_Field");
      Set<String> result = SpeciesListTransformer.transformLegacyKeys(keys);
      
      assertEquals(4, result.size());
      assertTrue(result.contains("custom_field_name"));
      assertTrue(result.contains("custom field name"));
      assertTrue(result.contains("Another_Field"));
      assertTrue(result.contains("Another Field"));
    }

    @Test
    @DisplayName("Should handle rawSupplied_Name, rawRank, rawScientific_Name and their underscores")
    void shouldHandleExtendedRawTaxonomicFields() {
      List<String> keys = Arrays.asList("rawSupplied_Name", "rawRank", "rawScientific_Name");
      Set<String> result = SpeciesListTransformer.transformLegacyKeys(keys);
      
      assertEquals(8, result.size());
      assertTrue(result.contains("rawSupplied_Name"));
      assertTrue(result.contains("rawSupplied Name"));
      assertTrue(result.contains("Supplied Name"));
      
      assertTrue(result.contains("rawRank"));
      assertTrue(result.contains("Rank"));
      
      assertTrue(result.contains("rawScientific_Name"));
      assertTrue(result.contains("rawScientific Name"));
      assertTrue(result.contains("Scientific Name"));
    }

    @Test
    @DisplayName("Should handle null or empty collections")
    void shouldHandleNullOrEmpty() {
      assertTrue(SpeciesListTransformer.transformLegacyKeys(null).isEmpty());
      assertTrue(SpeciesListTransformer.transformLegacyKeys(Collections.emptyList()).isEmpty());
    }

    @Test
    @DisplayName("Should handle case variations in raw taxonomic field names")
    void shouldHandleCaseVariations() {
      List<String> keys = Arrays.asList("RawFamily", "rawKingdom", "RAWORDER");
      Set<String> result = SpeciesListTransformer.transformLegacyKeys(keys);
      
      assertEquals(6, result.size());
      assertTrue(result.contains("RawFamily"));
      assertTrue(result.contains("Family"));
      assertTrue(result.contains("rawKingdom"));
      assertTrue(result.contains("Kingdom"));
      assertTrue(result.contains("RAWORDER"));
      assertTrue(result.contains("ORDER"));
    }
  }

  @Nested
  @DisplayName("KVP Values Duplication Tests for Raw Taxonomic Fields")
  class KvpValuesDuplicationTests {
    
    private SpeciesListItem createTestItem() {
      SpeciesListItem item = new SpeciesListItem();
      item.setId(new ObjectId());
      item.setSpeciesListID("test-list-id");
      item.setScientificName("Felis catus");
      
      Classification classification = new Classification();
      classification.setTaxonConceptID("urn:lsid:biodiversity.org.au:afd.taxon:12345");
      classification.setScientificName("Felis catus");
      item.setClassification(classification);
      
      return item;
    }
    
    private void mockSpeciesList() {
      SpeciesList speciesList = new SpeciesList();
      speciesList.setId("test-list-id");
      speciesList.setDataResourceUid("dr123");
      speciesList.setTitle("Test List");
      when(speciesListMongoRepository.findByIdOrDataResourceUid(anyString(), anyString()))
          .thenReturn(Optional.of(speciesList));
    }
    
    @Test
    @DisplayName("Should duplicate rawFamily to family when family doesn't exist")
    void shouldDuplicateRawFamilyToFamily() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawfamily", "Felidae"));
      properties.add(new KeyValue("otherProperty", "someValue"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // Should have: rawfamily (preserved), family (duplicate), otherProperty
      assertEquals(3, result.getKvpValues().size());
      
      // Check that we have rawfamily and family
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "rawfamily".equals(kv.getKey())));
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "family".equals(kv.getKey())));
      
      // All family values should be "Felidae"
      result.getKvpValues().stream()
          .filter(kv -> "rawfamily".equals(kv.getKey()) || "family".equals(kv.getKey()))
          .forEach(kv -> assertEquals("Felidae", kv.getValue()));
    }
    
    @Test
    @DisplayName("Should NOT duplicate rawFamily when family already exists")
    void shouldNotDuplicateWhenFamilyExists() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawfamily", "RawFelidae"));
      properties.add(new KeyValue("family", "DirectFelidae"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // Should have: rawfamily (preserved), family (direct) - NO duplicate since family already exists
      assertEquals(2, result.getKvpValues().size());
      
      // Check we have rawfamily and family with their respective values
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "rawfamily".equals(kv.getKey()) && "RawFelidae".equals(kv.getValue())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "family".equals(kv.getKey()) && "DirectFelidae".equals(kv.getValue())));
    }
    
    @Test
    @DisplayName("Should duplicate all raw taxonomic fields")
    void shouldDuplicateAllRawTaxonomicFields() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawkingdom", "Animalia"));
      properties.add(new KeyValue("rawphylum", "Chordata"));
      properties.add(new KeyValue("rawclass", "Mammalia"));
      properties.add(new KeyValue("raworder", "Carnivora"));
      properties.add(new KeyValue("rawfamily", "Felidae"));
      properties.add(new KeyValue("rawgenus", "Felis"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // All raw fields should be preserved AND have non-raw duplicates added
      // Total: rawkingdom, kingdom, rawphylum, phylum, rawclass, class, raworder, order, rawfamily, family, rawgenus, genus = 12
      assertEquals(12, result.getKvpValues().size());
      
      // Check that all raw fields exist
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "rawkingdom".equals(kv.getKey())), "Should have rawkingdom");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "rawphylum".equals(kv.getKey())), "Should have rawphylum");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "rawclass".equals(kv.getKey())), "Should have rawclass");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "raworder".equals(kv.getKey())), "Should have raworder");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "rawfamily".equals(kv.getKey())), "Should have rawfamily");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "rawgenus".equals(kv.getKey())), "Should have rawgenus");
      
      // Check that all non-raw duplicates exist
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "kingdom".equals(kv.getKey())), "Should have kingdom duplicate");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "phylum".equals(kv.getKey())), "Should have phylum duplicate");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "class".equals(kv.getKey())), "Should have class duplicate");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "order".equals(kv.getKey())), "Should have order duplicate");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "family".equals(kv.getKey())), "Should have family duplicate");
      assertTrue(result.getKvpValues().stream().anyMatch(kv -> "genus".equals(kv.getKey())), "Should have genus duplicate");
      
      // Verify values
      assertKvpValue(result.getKvpValues(), "kingdom", "Animalia");
      assertKvpValue(result.getKvpValues(), "phylum", "Chordata");
      assertKvpValue(result.getKvpValues(), "class", "Mammalia");
      assertKvpValue(result.getKvpValues(), "order", "Carnivora");
      assertKvpValue(result.getKvpValues(), "family", "Felidae");
      assertKvpValue(result.getKvpValues(), "genus", "Felis");
    }
    
    @Test
    @DisplayName("Should handle mix of raw and non-raw fields correctly")
    void shouldHandleMixOfRawAndNonRawFields() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawfamily", "Felidae"));    // Will be transformed to family + duplicated as family (total 2x family)
      properties.add(new KeyValue("kingdom", "Animalia"));      // Already non-raw, no duplicate
      properties.add(new KeyValue("raworder", "Carnivora"));    // Not transformed by fixLegacyKeys, but duplicated as order
      properties.add(new KeyValue("customField", "value"));     // Not taxonomic, no duplicate
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // rawfamily (preserved) + family (duplicate) + kingdom + raworder (preserved) + order (duplicate) + customField = 6
      assertEquals(6, result.getKvpValues().size());
      
      assertEquals(1, countKeyOccurrences(result.getKvpValues(), "rawfamily"), "rawfamily should appear once");
      assertEquals(1, countKeyOccurrences(result.getKvpValues(), "family"), "family should appear once (duplicate of rawfamily)");
      assertEquals(1, countKeyOccurrences(result.getKvpValues(), "kingdom"), "kingdom should appear once (no raw version)");
      assertEquals(1, countKeyOccurrences(result.getKvpValues(), "raworder"), "raworder should appear once");
      assertEquals(1, countKeyOccurrences(result.getKvpValues(), "order"), "order should appear once (duplicate of raworder)");
      assertEquals(1, countKeyOccurrences(result.getKvpValues(), "customField"), "customField should appear once");
    }
    
    @Test
    @DisplayName("Should handle empty properties list")
    void shouldHandleEmptyProperties() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      item.setProperties(new ArrayList<>());
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      assertTrue(result.getKvpValues().isEmpty());
    }
    
    @Test
    @DisplayName("Should handle null properties")
    void shouldHandleNullProperties() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      item.setProperties(null);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      assertTrue(result.getKvpValues().isEmpty());
    }
    
    @Test
    @DisplayName("Should apply legacy key transformations along with duplication")
    void shouldApplyLegacyKeyTransformations() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawfamily", "Felidae"));
      properties.add(new KeyValue("taxonRank", "species"));
      properties.add(new KeyValue("CommonNames", "Cat"));
      properties.add(new KeyValue("group", "Mammal"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // rawfamily (preserved) + family (duplicate) + taxonRank (preserved) + rank (duplicate) + CommonNames (preserved) + common name (duplicate) + group (preserved) + Group (duplicate) = 8
      assertEquals(8, result.getKvpValues().size());
      
      // Check legacy transformations
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "rawfamily".equals(kv.getKey())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "family".equals(kv.getKey())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "taxonRank".equals(kv.getKey()) && "species".equals(kv.getValue())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "rank".equals(kv.getKey()) && "species".equals(kv.getValue())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "CommonNames".equals(kv.getKey()) && "Cat".equals(kv.getValue())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "common name".equals(kv.getKey()) && "Cat".equals(kv.getValue())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "group".equals(kv.getKey()) && "Mammal".equals(kv.getValue())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "Group".equals(kv.getKey()) && "Mammal".equals(kv.getValue())));
    }
    
    @Test
    @DisplayName("Should handle case variations in raw taxonomic field names")
    void shouldHandleCaseVariations() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("RawFamily", "Felidae"));      // Different case
      properties.add(new KeyValue("rawKingdom", "Animalia"));    // Mixed case
      properties.add(new KeyValue("RAWORDER", "Carnivora"));     // Uppercase
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // Should recognize all variations as raw taxonomic fields and duplicate them
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "family".equalsIgnoreCase(kv.getKey()) && "Felidae".equals(kv.getValue())),
          "Should have family duplicate from RawFamily");
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "kingdom".equalsIgnoreCase(kv.getKey()) && "Animalia".equals(kv.getValue())),
          "Should have kingdom duplicate from rawKingdom");
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "order".equalsIgnoreCase(kv.getKey()) && "Carnivora".equals(kv.getValue())),
          "Should have order duplicate from RAWORDER");
    }
    
    @Test
    @DisplayName("Should NOT duplicate if non-raw version exists in different case")
    void shouldNotDuplicateIfNonRawExistsInDifferentCase() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawfamily", "RawFelidae"));
      properties.add(new KeyValue("Family", "DirectFelidae"));  // Different case but should match "family"
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // Should have: rawfamily (preserved), Family (as-is) - NO additional duplicate since "family" exists (case-insensitive check)
      assertEquals(2, result.getKvpValues().size());
      
      // Should have rawfamily and Family
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "rawfamily".equals(kv.getKey())));
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> "Family".equals(kv.getKey())));
    }
    
    @Test
    @DisplayName("Should add space-separated duplicate for keys with underscores")
    void shouldDuplicateKeysWithUnderscores() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("custom_field_name", "value1"));
      properties.add(new KeyValue("Another_Field", "value2"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // Should have: custom_field_name, custom field name, Another_Field, Another Field = 4
      assertEquals(4, result.getKvpValues().size());
      
      assertKvpValue(result.getKvpValues(), "custom_field_name", "value1");
      assertKvpValue(result.getKvpValues(), "custom field name", "value1");
      assertKvpValue(result.getKvpValues(), "Another_Field", "value2");
      assertKvpValue(result.getKvpValues(), "Another Field", "value2");
    }

    @Test
    @DisplayName("Should handle rawSupplied_Name, rawRank, rawScientific_Name and their underscores")
    void shouldHandleExtendedRawTaxonomicFields() {
      mockSpeciesList();
      SpeciesListItem item = createTestItem();
      
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue("rawSupplied_Name", "Eucalyptus globulus"));
      properties.add(new KeyValue("rawRank", "species"));
      properties.add(new KeyValue("rawScientific_Name", "E. globulus"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      // Should have: 
      // 1. rawSupplied_Name
      // 2. rawSupplied Name (due to underscore replacement of rawSupplied_Name)
      // 3. Supplied Name (due to raw stripping and underscore replacement of rawSupplied_Name)
      // 4. rawRank
      // 5. Rank (due to raw stripping)
      // 6. rawScientific_Name
      // 7. rawScientific Name (due to underscore replacement of rawScientific_Name)
      // 8. Scientific Name (due to raw stripping and underscore replacement of rawScientific_Name)
      
      assertEquals(8, result.getKvpValues().size());
      
      assertKvpValue(result.getKvpValues(), "rawSupplied_Name", "Eucalyptus globulus");
      assertKvpValue(result.getKvpValues(), "rawSupplied Name", "Eucalyptus globulus");
      assertKvpValue(result.getKvpValues(), "Supplied Name", "Eucalyptus globulus");
      
      assertKvpValue(result.getKvpValues(), "rawRank", "species");
      assertKvpValue(result.getKvpValues(), "Rank", "species");
      
      assertKvpValue(result.getKvpValues(), "rawScientific_Name", "E. globulus");
      assertKvpValue(result.getKvpValues(), "rawScientific Name", "E. globulus");
      assertKvpValue(result.getKvpValues(), "Scientific Name", "E. globulus");
    }
    
    private void assertTransformation(String inputKey, String expectedOutputKey) {
      SpeciesListItem item = createTestItem();
      List<KeyValue> properties = new ArrayList<>();
      properties.add(new KeyValue(inputKey, "test_val"));
      item.setProperties(properties);
      
      QueryListItemVersion1 result = transformer.transformToQueryListVersion1(item);
      
      assertTrue(result.getKvpValues().stream()
          .anyMatch(kv -> expectedOutputKey.equals(kv.getKey()) && "test_val".equals(kv.getValue())),
          String.format("Expected to find transformed kvp with key='%s' from input='%s'", expectedOutputKey, inputKey));
    }

    @Test
    @DisplayName("Should apply specific key transformations based on provided mapping")
    void shouldApplySpecificKeyTransformations() {
      mockSpeciesList();
      
      assertTransformation("VernacularName", "vernacular name");
      assertTransformation("Supplied_common_name", "Supplied common name");
      assertTransformation("Other_Name", "Other Name");
      assertTransformation("CommonNames", "common names");
      assertTransformation("CommonName", "common name");
      assertTransformation("vernacularName", "vernacular name");
      assertTransformation("Full_name", "Full name");
      assertTransformation("supplied_species_name", "supplied species name");
      assertTransformation("Display_Name", "Display Name");
      assertTransformation("matchedName", "matched name");
      assertTransformation("rawSupplied_Name", "Supplied Name");
    }
    
    private long countKeyOccurrences(List<KvpValueVersion1> kvps, String key) {
      return kvps.stream().filter(kv -> key.equals(kv.getKey())).count();
    }
    
    private void assertKvpValue(List<KvpValueVersion1> kvps, String key, String expectedValue) {
      assertTrue(kvps.stream()
          .anyMatch(kv -> key.equals(kv.getKey()) && expectedValue.equals(kv.getValue())),
          String.format("Expected to find kvp with key='%s' and value='%s'", key, expectedValue));
    }
  }
}

