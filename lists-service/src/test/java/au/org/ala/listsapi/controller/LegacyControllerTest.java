package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.service.SearchHelperService;
import au.org.ala.listsapi.service.SpeciesListLegacyService;

@ExtendWith(MockitoExtension.class)
class LegacyControllerTest {

    @Mock
    private SearchHelperService searchHelperService;

    @Mock
    private SpeciesListLegacyService legacyService;

    @Mock
    private AuthUtils authUtils;

    @Mock
    private Principal principal;

    @InjectMocks
    private LegacyController legacyController;

    @Captor
    private ArgumentCaptor<SpeciesList> speciesListCaptor;

    @Test
    void speciesListSearch_withListType_parsesAndPassesListType() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 25);
        when(searchHelperService.searchDocuments(any(), any(), any(), anyString(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));
        when(authUtils.getUserProfile(principal)).thenReturn(null);
        when(authUtils.hasAdminRole(null)).thenReturn(false);

        // Act
        legacyController.speciesListSearch(
                null, null, null, null, null, "eq:PROFILE", null, null, "listName", "asc", 25, 0, principal);

        // Assert
        verify(searchHelperService).searchDocuments(speciesListCaptor.capture(), any(), any(), anyString(), any());
        SpeciesList capturedQuery = speciesListCaptor.getValue();
        
        assertEquals("PROFILE", capturedQuery.getListType(), "ListType should be parsed correctly, removing 'eq:'");
    }
}
