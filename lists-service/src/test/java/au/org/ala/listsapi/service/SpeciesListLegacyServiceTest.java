package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import au.org.ala.listsapi.model.SpeciesItemVersion1;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.model.SpeciesListItemVersion1;
import au.org.ala.listsapi.model.SpeciesListVersion1;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import au.org.ala.listsapi.util.SpeciesListTransformer;

@ExtendWith(MockitoExtension.class)
class SpeciesListLegacyServiceTest {

    @Mock
    private SpeciesListTransformer speciesListTransformer;

    @Mock
    private SpeciesListMongoRepository speciesListMongoRepository;

    @InjectMocks
    private SpeciesListLegacyService speciesListLegacyService;

    private SpeciesList speciesList;
    private SpeciesListItem speciesListItem;

    @BeforeEach
    void setUp() {
        speciesList = new SpeciesList();
        speciesList.setId("list1");
        speciesList.setDataResourceUid("dr1");

        speciesListItem = new SpeciesListItem();
        speciesListItem.setId(new org.bson.types.ObjectId());
        speciesListItem.setSpeciesListID("list1");
    }

    @Test
    void testConvertListToVersion1_Single() {
        SpeciesListVersion1 v1 = new SpeciesListVersion1();
        when(speciesListTransformer.transformToVersion1(speciesList)).thenReturn(v1);

        SpeciesListVersion1 result = speciesListLegacyService.convertListToVersion1(speciesList);

        assertNotNull(result);
        verify(speciesListTransformer).transformToVersion1(speciesList);
    }

    @Test
    void testConvertListToVersion1_Multiple() {
        SpeciesListVersion1 v1 = new SpeciesListVersion1();
        when(speciesListTransformer.transformToVersion1(any(SpeciesList.class))).thenReturn(v1);

        List<SpeciesListVersion1> result = speciesListLegacyService.convertListToVersion1(Arrays.asList(speciesList, speciesList));

        assertEquals(2, result.size());
        verify(speciesListTransformer, times(2)).transformToVersion1(any(SpeciesList.class));
    }

    @Test
    void testConvertListItemToVersion1() {
        SpeciesListItemVersion1 itemV1 = new SpeciesListItemVersion1();
        when(speciesListTransformer.transformToVersion1(eq(speciesListItem), eq(0), any())).thenReturn(itemV1);

        List<SpeciesListItemVersion1> result = speciesListLegacyService.convertListItemToVersion1(Arrays.asList(speciesListItem));

        assertEquals(1, result.size());
        verify(speciesListTransformer).transformToVersion1(eq(speciesListItem), eq(0), any());
    }

    @Test
    void testConvertToSpeciesItemVersion1() {
        SpeciesItemVersion1 itemV1 = new SpeciesItemVersion1();
        
        when(speciesListMongoRepository.findByDataResourceUidInOrIdIn(any(Set.class)))
                .thenReturn(Arrays.asList(speciesList));
                
        when(speciesListTransformer.transformToSpeciesItemVersion1(eq(speciesListItem), any()))
                .thenReturn(itemV1);

        List<SpeciesItemVersion1> result = speciesListLegacyService.convertToSpeciesItemVersion1(Arrays.asList(speciesListItem));

        assertEquals(1, result.size());
        verify(speciesListMongoRepository).findByDataResourceUidInOrIdIn(argThat(set -> set.contains("list1")));
        verify(speciesListTransformer).transformToSpeciesItemVersion1(eq(speciesListItem), any());
    }
}
