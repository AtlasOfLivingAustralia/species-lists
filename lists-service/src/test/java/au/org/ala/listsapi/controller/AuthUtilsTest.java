/**
 * Copyright (c) 2025 Atlas of Living Australia
 * All Rights Reserved.
 * 
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.listsapi.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.ws.security.profile.AlaUserProfile;

@ExtendWith(MockitoExtension.class)
class AuthUtilsTest {

    @InjectMocks
    private AuthUtils authUtils;

    @Mock
    private AlaUserProfile mockProfile;

    private SpeciesList speciesList;

    private final String testUserId = "user123";
    private final String ownerUserId = "owner456";
    private final String editorUserId = "editor789";
    private final String adminUserId = "adminUser007";
    private final String adminRole = "ROLE_ADMIN";
    private final String userRole = "ROLE_USER";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authUtils, "adminRoles", List.of(adminRole));
        speciesList = new SpeciesList();
        speciesList.setOwner(ownerUserId);
        speciesList.setEditors(List.of(editorUserId));
    }

    private Principal createPrincipal(AlaUserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new PreAuthenticatedAuthenticationToken(profile, "credentials");
    }

    @Nested
    @DisplayName("Admin User Tests")
    class AdminUserTests {
        @Test
        @DisplayName("Admin user should be authorized regardless of list ownership or editor status")
        void adminUser_isAuthorized() {
            lenient().when(mockProfile.getUserId()).thenReturn(adminUserId);
            when(mockProfile.getRoles()).thenReturn(Set.of(adminRole));
            Principal adminPrincipal = createPrincipal(mockProfile);
            assertTrue(authUtils.isAuthorized(speciesList, adminPrincipal));
        }
    }

    @Nested
    @DisplayName("M2M Token with Internal Scope Tests")
    class InternalScopeTests {
        @Test
        @DisplayName("M2M token with ala/internal scope should be authorized regardless of list ownership")
        void m2mTokenWithInternalScope_isAuthorized() {
            lenient().when(mockProfile.getUserId()).thenReturn(null); // M2M tokens don't have user IDs
            when(mockProfile.getRoles()).thenReturn(Set.of("ala/internal"));
            Principal m2mPrincipal = createPrincipal(mockProfile);
            assertTrue(authUtils.isAuthorized(speciesList, m2mPrincipal));
        }

        @Test
        @DisplayName("M2M token with ala/internal scope should pass isAuthorized(Principal) check")
        void m2mTokenWithInternalScope_isAuthorizedPrincipal() {
            when(mockProfile.getRoles()).thenReturn(Set.of("ala/internal"));
            Principal m2mPrincipal = createPrincipal(mockProfile);
            assertTrue(authUtils.isAuthorized(m2mPrincipal));
        }

        @Test
        @DisplayName("M2M token without ala/internal scope should NOT be authorized")
        void m2mTokenWithoutInternalScope_isNotAuthorized() {
            lenient().when(mockProfile.getUserId()).thenReturn(null);
            when(mockProfile.getRoles()).thenReturn(Set.of("some/other/scope"));
            Principal m2mPrincipal = createPrincipal(mockProfile);
            assertFalse(authUtils.isAuthorized(speciesList, m2mPrincipal));
        }

        @Test
        @DisplayName("hasInternalScope should return true for ala/internal scope")
        void hasInternalScope_withInternalScope_returnsTrue() {
            when(mockProfile.getRoles()).thenReturn(Set.of("ala/internal"));
            assertTrue(authUtils.hasInternalScope(mockProfile));
        }

        @Test
        @DisplayName("hasInternalScope should return false for other scopes")
        void hasInternalScope_withoutInternalScope_returnsFalse() {
            when(mockProfile.getRoles()).thenReturn(Set.of("some/other/scope"));
            assertFalse(authUtils.hasInternalScope(mockProfile));
        }

        @Test
        @DisplayName("hasInternalScope should return false for null profile")
        void hasInternalScope_withNullProfile_returnsFalse() {
            assertFalse(authUtils.hasInternalScope(null));
        }
    }

    @Nested
    @DisplayName("Authenticated User Tests (Non-Admin)")
    class AuthenticatedUserTests {

        @Test
        @DisplayName("User who is the owner of the list should be authorized")
        void ownerUser_isAuthorized() {
            when(mockProfile.getUserId()).thenReturn(ownerUserId);
            lenient().when(mockProfile.getRoles()).thenReturn(Set.of(userRole)); // Non-admin role
            Principal ownerPrincipal = createPrincipal(mockProfile);

            assertTrue(authUtils.isAuthorized(speciesList, ownerPrincipal));
        }

        @Test
        @DisplayName("User who is an editor of the list should be authorized")
        void editorUser_isAuthorized() {
            when(mockProfile.getUserId()).thenReturn(editorUserId);
            lenient().when(mockProfile.getRoles()).thenReturn(Set.of(userRole)); // Non-admin role
            Principal editorPrincipal = createPrincipal(mockProfile);

            assertTrue(authUtils.isAuthorized(speciesList, editorPrincipal));
        }

        @Test
        @DisplayName("User who is neither owner nor editor should NOT be authorized")
        void nonOwnerNonEditorUser_isNotAuthorized() {
            when(mockProfile.getUserId()).thenReturn(testUserId); // Different user
            when(mockProfile.getRoles()).thenReturn(Set.of(userRole)); // Non-admin role
            Principal otherUserPrincipal = createPrincipal(mockProfile);

            assertFalse(authUtils.isAuthorized(speciesList, otherUserPrincipal));
        }

        @Test
        @DisplayName("User is owner, list has null editors, should be authorized")
        void ownerUser_listHasNullEditors_isAuthorized() {
            speciesList.setEditors(null);
            when(mockProfile.getUserId()).thenReturn(ownerUserId);
            lenient().when(mockProfile.getRoles()).thenReturn(Set.of(userRole));
            Principal ownerPrincipal = createPrincipal(mockProfile);

            assertTrue(authUtils.isAuthorized(speciesList, ownerPrincipal));
        }

        @Test
        @DisplayName("User is editor, list has null owner, should be authorized")
        void editorUser_listHasNullOwner_isAuthorized() {
            speciesList.setOwner(null);
            when(mockProfile.getUserId()).thenReturn(editorUserId);
            lenient().when(mockProfile.getRoles()).thenReturn(Set.of(userRole));
            Principal editorPrincipal = createPrincipal(mockProfile);

            assertTrue(authUtils.isAuthorized(speciesList, editorPrincipal));
        }

        @Test
        @DisplayName("User is neither owner nor editor, list has null owner and null editors, should NOT be authorized")
        void nonOwnerNonEditorUser_listHasNullOwnerAndNullEditors_isNotAuthorized() {
            speciesList.setOwner(null);
            speciesList.setEditors(null);
            lenient().when(mockProfile.getUserId()).thenReturn(testUserId);
            lenient().when(mockProfile.getRoles()).thenReturn(Set.of(userRole));
            Principal otherUserPrincipal = createPrincipal(mockProfile);

            assertFalse(authUtils.isAuthorized(speciesList, otherUserPrincipal));
        }
    }

    @Nested
    @DisplayName("Unauthenticated or Null Profile Tests")
    class UnauthenticatedUserTests {
        @Test
        @DisplayName("Null principal should NOT be authorized")
        void nullPrincipal_isNotAuthorized() {
            assertFalse(authUtils.isAuthorized(speciesList, null));
        }

        @Test
        @DisplayName("Principal with null profile should NOT be authorized")
        void principalWithNullProfile_isNotAuthorized() {
            Principal principalWithNullProfile = mock(Principal.class); // A generic principal that won't cast to AlaUserProfile container
            assertFalse(authUtils.isAuthorized(speciesList, principalWithNullProfile));
        }
    }
}