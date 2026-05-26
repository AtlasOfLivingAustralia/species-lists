/**
 * Copyright (c) 2025 Atlas of Living Australia All Rights Reserved.
 *
 * <p>The contents of this file are subject to the Mozilla Public License Version 1.1 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 */
package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import au.org.ala.listsapi.model.KeyValue;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.names.ws.api.NameSearch;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for TaxonService, focusing on the buildNameSearch method
 * and its handling of taxonomic fields from direct fields and properties.
 */
@ExtendWith(MockitoExtension.class)
class TaxonServiceTest {

    private TaxonService taxonService;
    private Method buildNameSearchMethod;
    private SpeciesList speciesList;
    
    @Mock
    private SpeciesListItemMongoRepository speciesListItemMongoRepository;
    
    @Mock
    private SpeciesListMongoRepository speciesListMongoRepository;
    
    @Mock
    private ElasticsearchOperations elasticsearchOperations;
    
    @Mock
    private ProgressService progressService;
    
    @Mock
    private SearchHelperService searchHelperService;

    @BeforeEach
    void setUp() throws Exception {
        // Create TaxonService instance with mocked dependencies
        taxonService = new TaxonService();
        
        // Inject mocked dependencies using reflection
        ReflectionTestUtils.setField(taxonService, "speciesListItemMongoRepository", speciesListItemMongoRepository);
        ReflectionTestUtils.setField(taxonService, "speciesListMongoRepository", speciesListMongoRepository);
        ReflectionTestUtils.setField(taxonService, "elasticsearchOperations", elasticsearchOperations);
        ReflectionTestUtils.setField(taxonService, "progressService", progressService);
        ReflectionTestUtils.setField(taxonService, "searchHelperService", searchHelperService);
        
        // Get the private buildNameSearch method via reflection
        buildNameSearchMethod = TaxonService.class.getDeclaredMethod(
                "buildNameSearch", SpeciesListItem.class, SpeciesList.class);
        buildNameSearchMethod.setAccessible(true);
        
        speciesList = new SpeciesList();
    }

    private NameSearch buildNameSearch(SpeciesListItem item) throws Exception {
        return (NameSearch) buildNameSearchMethod.invoke(taxonService, item, speciesList);
    }

    @Nested
    @DisplayName("Direct Field Tests")
    class DirectFieldTests {
        
        @Test
        @DisplayName("Should use direct family field when populated")
        void shouldUseDirectFamilyField() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily("Felidae");
            item.setScientificName("Felis catus");
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Felidae", result.getFamily());
        }
        
        @Test
        @DisplayName("Should use direct kingdom field when populated")
        void shouldUseDirectKingdomField() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setKingdom("Animalia");
            item.setScientificName("Felis catus");
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Animalia", result.getKingdom());
        }
        
        @Test
        @DisplayName("Should use all direct taxonomic fields when populated")
        void shouldUseAllDirectTaxonomicFields() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("Felis catus");
            item.setKingdom("Animalia");
            item.setPhylum("Chordata");
            item.setClasss("Mammalia");
            item.setOrder("Carnivora");
            item.setFamily("Felidae");
            item.setGenus("Felis");
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Animalia", result.getKingdom());
            assertEquals("Chordata", result.getPhylum());
            assertEquals("Mammalia", result.getClazz());
            assertEquals("Carnivora", result.getOrder());
            assertEquals("Felidae", result.getFamily());
            assertEquals("Felis", result.getGenus());
        }
    }

    @Nested
    @DisplayName("Raw Field Fallback Tests - For Migrated Lists")
    class RawFieldFallbackTests {
        
        @Test
        @DisplayName("Should use rawfamily from properties when family field is null")
        void shouldUseRawFamilyFromProperties() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily(null);
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawfamily", "Felidae"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Felidae", result.getFamily());
        }
        
        @Test
        @DisplayName("Should use rawkingdom from properties when kingdom field is null")
        void shouldUseRawKingdomFromProperties() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setKingdom(null);
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawkingdom", "Animalia"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Animalia", result.getKingdom());
        }
        
        @Test
        @DisplayName("Should use all raw taxonomic fields from properties when direct fields are null")
        void shouldUseAllRawTaxonomicFieldsFromProperties() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("Felis catus");
            item.setKingdom(null);
            item.setPhylum(null);
            item.setClasss(null);
            item.setOrder(null);
            item.setFamily(null);
            item.setGenus(null);
            
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawkingdom", "Animalia"));
            properties.add(new KeyValue("rawphylum", "Chordata"));
            properties.add(new KeyValue("rawclass", "Mammalia"));
            properties.add(new KeyValue("raworder", "Carnivora"));
            properties.add(new KeyValue("rawfamily", "Felidae"));
            properties.add(new KeyValue("rawgenus", "Felis"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Animalia", result.getKingdom());
            assertEquals("Chordata", result.getPhylum());
            assertEquals("Mammalia", result.getClazz());
            assertEquals("Carnivora", result.getOrder());
            assertEquals("Felidae", result.getFamily());
            assertEquals("Felis", result.getGenus());
        }
        
        @Test
        @DisplayName("Should handle case-insensitive raw field names")
        void shouldHandleCaseInsensitiveRawFieldNames() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily(null);
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("RawFamily", "Felidae"));  // Different case
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Felidae", result.getFamily());
        }
    }

    @Nested
    @DisplayName("Priority Tests - Direct Fields Over Raw Fields")
    class PriorityTests {
        
        @Test
        @DisplayName("Should prioritize direct family field over rawfamily in properties")
        void shouldPrioritizeDirectFamilyOverRawFamily() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily("DirectFelidae");
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawfamily", "PropertyFelidae"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("DirectFelidae", result.getFamily());
        }
        
        @Test
        @DisplayName("Should prioritize direct kingdom field over rawkingdom in properties")
        void shouldPrioritizeDirectKingdomOverRawKingdom() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setKingdom("DirectAnimalia");
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawkingdom", "PropertyAnimalia"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("DirectAnimalia", result.getKingdom());
        }
        
        @Test
        @DisplayName("Should prioritize all direct fields over raw fields when both exist")
        void shouldPrioritizeAllDirectFieldsOverRawFields() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("Felis catus");
            item.setKingdom("DirectAnimalia");
            item.setFamily("DirectFelidae");
            
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawkingdom", "PropertyAnimalia"));
            properties.add(new KeyValue("rawfamily", "PropertyFelidae"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("DirectAnimalia", result.getKingdom());
            assertEquals("DirectFelidae", result.getFamily());
        }
    }

    @Nested
    @DisplayName("Vernacular Name Mapping Tests")
    class VernacularNameMappingTests {
        
        @Test
        @DisplayName("Should use vernacular name if provided")
        void shouldUseVernacularNameIfProvided() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("Felis catus");
            item.setVernacularName("Cat");
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Felis catus", result.getScientificName());
            assertEquals("Cat", result.getVernacularName());
        }

        @Test
        @DisplayName("Should fallback to scientific name for vernacular name if vernacular is blank")
        void shouldFallbackToScientificNameForVernacularName() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("Common Name Test");
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Common Name Test", result.getScientificName());
            assertEquals("Common Name Test", result.getVernacularName());
        }
        
        @Test
        @DisplayName("Should fallback to scientific name for vernacular name even if scientific name is trimmed")
        void shouldFallbackToScientificNameForVernacularNameTrimmed() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("  Common Name Test  ");
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Common Name Test", result.getScientificName());
            assertEquals("Common Name Test", result.getVernacularName());
        }
    }

    @Nested
    @DisplayName("Null and Empty Handling Tests")
    class NullAndEmptyHandlingTests {
        
        @Test
        @DisplayName("Should return null for family when both direct and raw fields are null")
        void shouldReturnNullWhenBothFieldsNull() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily(null);
            item.setScientificName("Felis catus");
            item.setProperties(null);
            
            NameSearch result = buildNameSearch(item);
            
            assertNull(result.getFamily());
        }
        
        @Test
        @DisplayName("Should return null for family when direct field is null and no raw field exists")
        void shouldReturnNullWhenNoRawField() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily(null);
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("otherProperty", "someValue"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertNull(result.getFamily());
        }
        
        @Test
        @DisplayName("Should ignore blank raw field values")
        void shouldIgnoreBlankRawFieldValues() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily(null);
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawfamily", "  "));  // Blank value
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertNull(result.getFamily());
        }
        
        @Test
        @DisplayName("Should trim whitespace from raw field values")
        void shouldTrimWhitespaceFromRawFieldValues() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setFamily(null);
            item.setScientificName("Felis catus");
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawfamily", "  Felidae  "));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("Felidae", result.getFamily());
        }
    }

    @Nested
    @DisplayName("Integration with Other Fields Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Should handle mix of direct and raw fields correctly")
        void shouldHandleMixOfDirectAndRawFields() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setScientificName("Felis catus");
            item.setKingdom("DirectAnimalia");  // Direct field
            item.setPhylum(null);                // Will use raw
            item.setFamily("DirectFelidae");     // Direct field
            item.setGenus(null);                 // Will use raw
            
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawphylum", "Chordata"));
            properties.add(new KeyValue("rawgenus", "Felis"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("DirectAnimalia", result.getKingdom());
            assertEquals("Chordata", result.getPhylum());
            assertEquals("DirectFelidae", result.getFamily());
            assertEquals("Felis", result.getGenus());
        }
        
        @Test
        @DisplayName("Should include taxonID and scientificName along with taxonomic hierarchy")
        void shouldIncludeAllRelevantFields() throws Exception {
            SpeciesListItem item = new SpeciesListItem();
            item.setTaxonID("urn:lsid:biodiversity.org.au:afd.taxon:12345");
            item.setScientificName("Felis catus");
            item.setFamily(null);
            
            List<KeyValue> properties = new ArrayList<>();
            properties.add(new KeyValue("rawfamily", "Felidae"));
            item.setProperties(properties);
            
            NameSearch result = buildNameSearch(item);
            
            assertEquals("urn:lsid:biodiversity.org.au:afd.taxon:12345", result.getTaxonID());
            assertEquals("Felis catus", result.getScientificName());
            assertEquals("Felidae", result.getFamily());
        }
    }
}
