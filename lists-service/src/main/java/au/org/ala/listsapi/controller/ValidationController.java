package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class ValidationController {
  @Autowired protected ValidationService validationService;

  @Operation(tags = "Validation", summary = "Get all constraint lists")
  @ApiResponses({
          @ApiResponse(
                  responseCode = "200",
                  description = "Constraint lists",
                  content = @Content(
                          mediaType = MediaType.APPLICATION_JSON_VALUE,
                          schema = @Schema(implementation = JsonObject.class)
                  )
          ),
          @ApiResponse(responseCode = "400", description = "Bad Request - Invalid parameter")
  })
  @GetMapping("/v2/constraints")
  public ResponseEntity<Object> constraints() {
    try {
      return new ResponseEntity<>(
              validationService.getConstraintMap(),
              HttpStatus.OK
      );
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
