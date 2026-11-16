package au.org.ala.listsapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import au.org.ala.listsapi.service.UserdetailsService.UserLookupException;
import au.org.ala.listsapi.service.auth.WebService;

@ExtendWith(MockitoExtension.class)
class UserdetailsServiceTest {

    @Mock
    private WebService webService;

    @InjectMocks
    private UserdetailsService userdetailsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userdetailsService, "userdetailsUrl", "https://auth.ala.org.au/userdetails/");
    }

    private void stubResponse(Map<String, Object> response) {
        when(webService.post(anyString(), any(), anyMap(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(response);
    }

    @Nested
    @DisplayName("fetchUserById (strict)")
    class FetchUserById {

        @Test
        void found_returnsUserDetails() {
            stubResponse(Map.of("statusCode", 200, "resp", Map.of("userId", "62639")));

            Map result = userdetailsService.fetchUserById("62639");

            assertEquals("62639", result.get("userId"));
        }

        @Test
        void notFound_returnsNull() {
            stubResponse(Map.of("statusCode", 404, "error", "not found"));

            assertNull(userdetailsService.fetchUserById("99999999"));
        }

        @Test
        void serverErrorOrTimeout_throws() {
            // WebService.send maps timeouts/IO errors to statusCode 500
            stubResponse(Map.of("statusCode", 500, "error", "SocketTimeoutException"));

            assertThrows(UserLookupException.class, () -> userdetailsService.fetchUserById("62639"));
        }

        @Test
        void nonJsonResponse_throws() {
            stubResponse(Map.of("statusCode", 200, "resp", "<html>oops</html>"));

            assertThrows(UserLookupException.class, () -> userdetailsService.fetchUserById("62639"));
        }
    }

    @Nested
    @DisplayName("fetchUserByEmail (lenient)")
    class FetchUserByEmail {

        @Test
        void serverError_returnsNull() {
            stubResponse(Map.of("statusCode", 500, "error", "boom"));

            assertNull(userdetailsService.fetchUserByEmail("someone@example.com"));
        }
    }
}
