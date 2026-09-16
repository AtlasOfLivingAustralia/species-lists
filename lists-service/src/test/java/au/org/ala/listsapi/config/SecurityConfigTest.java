package au.org.ala.listsapi.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.pac4j.core.client.DirectClient;
import org.springframework.test.util.ReflectionTestUtils;

import au.org.ala.ws.security.client.AlaAuthClient;

class SecurityConfigTest {

    @Test
    void testInitPac4jClients_InitializesAuthClientAndDirectClients() {
        SecurityConfig securityConfig = new SecurityConfig();
        AlaAuthClient alaAuthClient = mock(AlaAuthClient.class);
        DirectClient directClient = mock(DirectClient.class);

        when(alaAuthClient.getAuthClients()).thenReturn(List.of(directClient));
        ReflectionTestUtils.setField(securityConfig, "alaAuthClient", alaAuthClient);

        securityConfig.initPac4jClients();

        verify(alaAuthClient).init();
        verify(directClient).init();
    }

    @Test
    void testInitPac4jClients_NullClientHandledGracefully() {
        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.initPac4jClients(); // should not throw
    }
}
