package au.org.ala.listsapi.controller;

import java.security.Principal;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import au.org.ala.listsapi.service.AdminService;
import au.org.ala.ws.security.profile.AlaUserProfile;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Admin REST API */
@CrossOrigin(origins = "*", maxAge = 3600)
@SecurityScheme(name = "JWT", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@RestController
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    protected AdminService adminService;

    @Autowired
    protected AuthUtils authUtils;

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
    @DeleteMapping("/admin/wipe/index")
    public ResponseEntity<Object> deleteIndex(@AuthenticationPrincipal Principal principal) {

        ResponseEntity<Object> errorResponse = checkAuthorized(principal);
        if (errorResponse != null)
            return errorResponse;

        logger.info("Deleting ES index...");
        adminService.deleteIndex();
        logger.info("Deleted ES index");

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Hidden
    @SecurityRequirement(name = "JWT")
    @Operation(summary = "Delete the MongoDB documents", tags = "Admin")
    @DeleteMapping("/admin/wipe/docs")
    public ResponseEntity<Object> deleteDocs(@AuthenticationPrincipal Principal principal) {

        ResponseEntity<Object> errorResponse = checkAuthorized(principal);
        if (errorResponse != null)
            return errorResponse;

        logger.info("Deleting all MongoDB documents...");
        adminService.deleteDocs();
        logger.info("Deleted all MongoDB documents");

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Hidden
    @SecurityRequirement(name = "JWT")
    @Operation(summary = "Trigger a reboot following index deletion", tags = "Admin")
    @PostMapping("/admin/reboot")
    public ResponseEntity<Object> reboot(@AuthenticationPrincipal Principal principal) {

        ResponseEntity<Object> errorResponse = checkAuthorized(principal);
        if (errorResponse != null)
            return errorResponse;

        logger.info("Rebooting lists... no longer supported");
        
        return ResponseEntity.status(500).body("Reboot function has been deprecated and is no longer available");
    }

    @Hidden
    @SecurityRequirement(name = "JWT")
    @Operation(summary = "Get indexes for MongoDB collections", tags = "Admin")
    @GetMapping("/admin/indexes")
    public ResponseEntity<Object> indexes(@AuthenticationPrincipal Principal principal) {

        ResponseEntity<Object> errorResponse = checkAuthorized(principal);
        if (errorResponse != null)
            return errorResponse;

        return new ResponseEntity<>(adminService.getMongoIndexes(), HttpStatus.OK);
    }
}
