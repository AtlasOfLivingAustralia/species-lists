package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.InputSpeciesList;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private Resource constraintsFile;

    @InjectMocks
    private ValidationService validationService;

    private final String mockJson = "{\n" +
            "  \"listType\": [\n" +
            "    {\"value\": \"TEST_TYPE\", \"label\": \"Test Type\"}\n" +
            "  ],\n" +
            "  \"licence\": [\n" +
            "    {\"value\": \"CC-BY\", \"label\": \"Creative Commons BY\"}\n" +
            "  ]\n" +
            "}";

    @BeforeEach
    void setUp() throws Exception {
        InputStream is = new ByteArrayInputStream(mockJson.getBytes(StandardCharsets.UTF_8));
        lenient().when(constraintsFile.getInputStream()).thenReturn(is);
        lenient().when(constraintsFile.getDescription()).thenReturn("mock file");

        // Manually trigger the @PostConstruct init method via reflection
        ReflectionTestUtils.invokeMethod(validationService, "init");
    }

    @Test
    void testIsValueValid() {
        assertTrue(validationService.isValueValid(ConstraintType.listType, "TEST_TYPE"));
        assertFalse(validationService.isValueValid(ConstraintType.listType, "INVALID_TYPE"));
        
        assertTrue(validationService.isValueValid(ConstraintType.licence, "CC-BY"));
        assertFalse(validationService.isValueValid(ConstraintType.licence, "INVALID_LICENCE"));
    }

    @Test
    void testIsListValid() {
        InputSpeciesList validList = new InputSpeciesList();
        validList.setListType("TEST_TYPE");
        validList.setLicence("CC-BY");
        assertTrue(validationService.isListValid(validList));

        InputSpeciesList invalidType = new InputSpeciesList();
        invalidType.setListType("INVALID");
        invalidType.setLicence("CC-BY");
        assertFalse(validationService.isListValid(invalidType));

        InputSpeciesList invalidLicence = new InputSpeciesList();
        invalidLicence.setListType("TEST_TYPE");
        invalidLicence.setLicence("INVALID");
        assertFalse(validationService.isListValid(invalidLicence));

        // Public list without licence is invalid
        InputSpeciesList publicListNoLicence = new InputSpeciesList();
        publicListNoLicence.setListType("TEST_TYPE");
        publicListNoLicence.setIsPrivate("false");
        assertFalse(validationService.isListValid(publicListNoLicence));

        InputSpeciesList publicListBlankLicence = new InputSpeciesList();
        publicListBlankLicence.setListType("TEST_TYPE");
        publicListBlankLicence.setIsPrivate("false");
        publicListBlankLicence.setLicence("   ");
        assertFalse(validationService.isListValid(publicListBlankLicence));

        // Private list without licence is valid
        InputSpeciesList privateListNoLicence = new InputSpeciesList();
        privateListNoLicence.setListType("TEST_TYPE");
        privateListNoLicence.setIsPrivate("true");
        assertTrue(validationService.isListValid(privateListNoLicence));

        InputSpeciesList privateListBlankLicence = new InputSpeciesList();
        privateListBlankLicence.setListType("TEST_TYPE");
        privateListBlankLicence.setIsPrivate("true");
        privateListBlankLicence.setLicence("   ");
        assertTrue(validationService.isListValid(privateListBlankLicence));

        // Private list with valid licence is valid
        InputSpeciesList privateListValidLicence = new InputSpeciesList();
        privateListValidLicence.setListType("TEST_TYPE");
        privateListValidLicence.setIsPrivate("true");
        privateListValidLicence.setLicence("CC-BY");
        assertTrue(validationService.isListValid(privateListValidLicence));

        // Private list with invalid licence is invalid
        InputSpeciesList privateListInvalidLicence = new InputSpeciesList();
        privateListInvalidLicence.setListType("TEST_TYPE");
        privateListInvalidLicence.setIsPrivate("true");
        privateListInvalidLicence.setLicence("INVALID");
        assertFalse(validationService.isListValid(privateListInvalidLicence));
    }

    @Test
    void testGetConstraintMap() {
        assertNotNull(validationService.getConstraintMap());
        assertEquals(2, validationService.getConstraintMap().size());
        assertTrue(validationService.getConstraintMap().containsKey("listType"));
        assertTrue(validationService.getConstraintMap().containsKey("licence"));
    }
}
