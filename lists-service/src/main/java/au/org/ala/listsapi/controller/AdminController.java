package au.org.ala.listsapi.controller;


import au.org.ala.listsapi.service.*;
import au.org.ala.ws.security.profile.AlaUserProfile;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.concurrent.CompletableFuture;

/** Admin REST API */
@CrossOrigin(origins = "*", maxAge = 3600)
@SecurityScheme(
    name = "JWT",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
@org.springframework.web.bind.annotation.RestController
public class AdminController {
  private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

  @Autowired protected AdminService adminService;

  @Autowired protected AuthUtils authUtils;

  @Nullable
  private ResponseEntity<Object> checkAuthorized(Principal principal) {
    // check user logged in
    AlaUserProfile alaUserProfile = (AlaUserProfile) principal;
    if (alaUserProfile == null) {
      return ResponseEntity.badRequest().body("User not found");
    }

    // check authorised
    if (!authUtils.isAuthorized(principal)) {
      return ResponseEntity.badRequest().body("User not authorized");
    }
    return null;
  }

  @Hidden
  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Delete the ES index", tags = "Admin")
  @DeleteMapping("/wipe/index")
  public ResponseEntity<Object> deleteIndex(@AuthenticationPrincipal Principal principal) {

    ResponseEntity<Object> errorResponse = checkAuthorized(principal);
    if (errorResponse != null) return errorResponse;

    logger.info("Deleting ES index...");
    adminService.deleteIndex();
    logger.info("Deleted ES index");

    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Hidden
  @SecurityRequirement(name = "JWT")
  @Operation(summary = "Delete the MongoDB documents", tags = "Admin")
  @DeleteMapping("/wipe/docs")
  public ResponseEntity<Object> deleteDocs(@AuthenticationPrincipal Principal principal) {

    ResponseEntity<Object> errorResponse = checkAuthorized(principal);
    if (errorResponse != null) return errorResponse;

    logger.info("Deleting all MongoDB documents...");
    adminService.deleteDocs();
    logger.info("Deleted all MongoDB documents");

    return new ResponseEntity<>(HttpStatus.OK);
  }
}
