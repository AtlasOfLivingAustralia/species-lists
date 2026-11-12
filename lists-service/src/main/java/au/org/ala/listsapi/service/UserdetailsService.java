/*
 * Copyright (C) 2025 Atlas of Living Australia
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
package au.org.ala.listsapi.service;

import java.util.Map;

import org.apache.hc.core5.http.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.service.auth.WebService;

@Service
public class UserdetailsService {
    private static final Logger logger = LoggerFactory.getLogger(UserdetailsService.class);

    @Autowired WebService webService;

    @Value("${userDetails.api.url}")
    private String userdetailsUrl;

    private Map requestUserDetails(String userName) {
        Map<String, Object> params = Map.of("userName", userName);
        return webService.post(
                userdetailsUrl + "userDetails/getUserDetails",
                null,
                params,
                ContentType.APPLICATION_JSON,
                true,
                false,
                null
        );
    }

    public Map fetchUserByEmail(String email) {
        Map request = requestUserDetails(email);

        if ((int)request.get("statusCode") == 200) {
            Object resp = request.get("resp");
            if (resp instanceof Map) {
                return (Map) resp;
            } else {
                logger.warn("Unexpected response type from user details API for username " + email + ": " + (resp != null ? resp.getClass().getName() : "null") + ", response: " + resp);
                return null;
            }
        }

        return null;
    }

    public Map fetchUserById(String userId) {
        Map request = requestUserDetails(userId);
        Object statusObj = request != null ? request.get("statusCode") : null;
        if (!(statusObj instanceof Integer)) {
            throw new UserLookupException("User details lookup for " + userId + " returned no status code");
        }

        int statusCode = (Integer) statusObj;
        if (statusCode == 404) {
            return null;
        }
        if (statusCode != 200) {
            throw new UserLookupException("User details lookup for " + userId + " failed: " + request.get("error"));
        }

        Object resp = request.get("resp");
        if (!(resp instanceof Map)) {
            throw new UserLookupException("Unexpected response from user details API for " + userId + ": " + resp);
        }
        return (Map) resp;
    }

    public static class UserLookupException extends RuntimeException {
        public UserLookupException(String message) {
            super(message);
        }
    }

    public static String getDisplayName(Map userDetails) {
        if (userDetails == null) {
            return null;
        }

        String displayName = trimToEmpty(userDetails.get("displayName"));
        if (!displayName.isEmpty()) {
            return displayName;
        }

        String fullName =
                (trimToEmpty(userDetails.get("firstName")) + " " + trimToEmpty(userDetails.get("lastName")))
                        .trim();
        return fullName.isEmpty() ? null : fullName;
    }

    private static String trimToEmpty(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }
}
