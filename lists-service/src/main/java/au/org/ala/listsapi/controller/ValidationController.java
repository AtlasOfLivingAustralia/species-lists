package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.ConstraintType;
import au.org.ala.listsapi.service.ValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class ValidationController {
  @Autowired protected ValidationService validationService;

  @Operation(tags = "Validation", summary = "Get all constraint lists")
  @Tag(name = "Validation", description = "Services for validating list parameters")
  @GetMapping("/constraints")
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
