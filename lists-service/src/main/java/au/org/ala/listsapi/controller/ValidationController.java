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
package au.org.ala.listsapi.controller;

import org.bson.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import au.org.ala.listsapi.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class ValidationController {
    @Autowired
    protected ValidationService validationService;

    @Operation(tags = "Validation", summary = "Get all constraint values for the service, including valid values for list constraints and any other relevant controlled vocabularies.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200", description = "Constraint values", 
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = JsonObject.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameter")
    })
    @GetMapping("/v2/constraints")
    public ResponseEntity<Object> constraints() {
        try {
            return new ResponseEntity<>(
                    validationService.getConstraintMap(),
                    HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
