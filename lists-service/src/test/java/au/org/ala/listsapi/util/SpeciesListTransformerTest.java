package au.org.ala.listsapi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.service.UserdetailsService;
import org.junit.jupiter.api.BeforeEach;
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

        assertNull(result.getSdsType());
        assertEquals(Boolean.FALSE, result.getIsSDS());
    }

    @Test
    void testTransformToVersion1_SdsNull() {
        SpeciesList speciesList = new SpeciesList();
        speciesList.setIsSDS(null);
        speciesList.setTitle("Test List");

        SpeciesListVersion1 result = transformer.transformToVersion1(speciesList);

        assertNull(result.getSdsType());
        assertNull(result.getIsSDS());
    }
}
