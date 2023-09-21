package au.org.ala.listsapi.controller;

import au.org.ala.listsapi.model.LegacySpeciesListQuery;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.model.SpeciesListItem;
import au.org.ala.listsapi.repo.SpeciesListItemMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Services added for backwards compatibility with the legacy lists API */
@CrossOrigin(origins = "*", maxAge = 3600)
@org.springframework.web.bind.annotation.RestController
public class RESTController {

  @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;

  @Autowired protected SpeciesListItemMongoRepository speciesListItemMongoRepository;

  @Tag(name = "REST services", description = "Get species list metadata")
  @GetMapping("/speciesList/{speciesListID}")
  public ResponseEntity<SpeciesList> speciesList(
      @PathVariable("speciesListID") String speciesListID) {
    Optional<SpeciesList> speciesList = speciesListMongoRepository.findById(speciesListID);
    return speciesList.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @Tag(name = "REST services", description = "Get species lists matching the query")
  @GetMapping("/speciesList/")
  public ResponseEntity<Object> speciesLists(
      LegacySpeciesListQuery speciesList,
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
    try {
      Pageable paging = PageRequest.of(page - 1, pageSize);
      if (speciesList == null || speciesList.isEmpty()) {
        Page<SpeciesList> results = speciesListMongoRepository.findAll(paging);
        return new ResponseEntity<>(results.getContent(), HttpStatus.OK);
      }

      ExampleMatcher matcher =
          ExampleMatcher.matching()
              .withIgnoreCase()
              .withIgnoreNullValues()
              .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);
      // Create an Example from the exampleProduct with the matcher
      Example<SpeciesList> example = Example.of(speciesList.convertTo(), matcher);
      Page<SpeciesList> results = speciesListMongoRepository.findAll(example, paging);
      return new ResponseEntity<>(results.getContent(), HttpStatus.OK);
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @Tag(name = "REST services", description = "Get species lists items for a list")
  @GetMapping("/speciesListItems/{speciesListID}")
  public ResponseEntity<Object> speciesList(
      @PathVariable("speciesListID") String speciesListID,
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "pageSize", defaultValue = "10") int pageSize) {
    try {
      Pageable paging = PageRequest.of(page - 1, pageSize);
      Page<SpeciesListItem> speciesListItems =
          speciesListItemMongoRepository.findBySpeciesListID(speciesListID, paging);
      return new ResponseEntity<>(speciesListItems.getContent(), HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
