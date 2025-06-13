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

package au.org.ala.listsapi.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Represents an error response for API calls.
 */
@NoArgsConstructor
@Data
@SuperBuilder
@AllArgsConstructor
@Jacksonized
public class ErrorResponse {
    // Error response fields, e.g.NotFound, BadRequest, etc.
    private String error;
    // A human-readable message describing the error
    private String message;
    // The HTTP status code associated with the error
    private int status;
}
