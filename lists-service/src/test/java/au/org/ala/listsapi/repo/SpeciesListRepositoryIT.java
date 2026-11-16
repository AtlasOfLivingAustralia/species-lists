package au.org.ala.listsapi.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.support.AbstractMongoIT;

/**
 * The species list queries against a real MongoDB: the declared ones on {@link
 * SpeciesListMongoRepository} and the criteria {@link SpeciesListCustomRepository} builds by hand.
 */
@Import(SpeciesListCustomRepository.class)
class SpeciesListRepositoryIT extends AbstractMongoIT {

    @Autowired private SpeciesListMongoRepository listRepository;

    @Autowired private SpeciesListCustomRepository customRepository;

    @BeforeEach
    void clearLists() {
        listRepository.deleteAll();
    }

    private SpeciesList saved(String id, String drUid, String title, String description) {
        return listRepository.save(
                SpeciesList.builder()
                        .id(id)
                        .dataResourceUid(drUid)
                        .title(title)
                        .description(description)
                        .listType("TEST")
                        .isPrivate(false)
                        .build());
    }

    @Nested
    @DisplayName("Declared queries")
    class DeclaredQueries {

        @Test
        void findByIdOrDataResourceUid_matchesEitherIdentifier() {
            saved("dr100", "dr100-uid", "Acacia of the ACT", "wattles");

            assertTrue(listRepository.findByIdOrDataResourceUid("dr100", "nothing").isPresent());
            assertTrue(listRepository.findByIdOrDataResourceUid("nothing", "dr100-uid").isPresent());
            assertTrue(listRepository.findByIdOrDataResourceUid("nothing", "nothing").isEmpty());
        }

        @Test
        void findByDataResourceUidInOrIdIn_matchesOnEitherField() {
            saved("dr200", "dr200-uid", "Grasses", null);
            saved("dr201", "dr201-uid", "Sedges", null);
            saved("dr202", "dr202-uid", "Rushes", null);

            // One identifier of each kind, in a single collection, which is what the hand-written
            // $or query exists for
            List<SpeciesList> found =
                    listRepository.findByDataResourceUidInOrIdIn(List.of("dr200", "dr201-uid"));

            assertEquals(2, found.size());
            assertTrue(found.stream().anyMatch(list -> "dr200".equals(list.getId())));
            assertTrue(found.stream().anyMatch(list -> "dr201".equals(list.getId())));
        }

        @Test
        void findAllByDataResourceUidIsIn_ignoresUnknownUids() {
            saved("dr300", "dr300-uid", "Ferns", null);

            assertEquals(
                    1, listRepository.findAllByDataResourceUidIsIn(List.of("dr300-uid", "absent")).size());
            assertTrue(listRepository.findAllByDataResourceUidIsIn(List.of("absent")).isEmpty());
        }
    }

    @Nested
    @DisplayName("Criteria built by SpeciesListCustomRepository")
    class CustomCriteria {

        @Test
        void findByExample_matchesPartOfAFieldIgnoringCase() {
            saved("dr400", "dr400-uid", "Threatened flora", null);
            saved("dr401", "dr401-uid", "Invasive fauna", null);

            // The matcher is CONTAINING + ignore case, so a fragment in the wrong case matches
            Page<SpeciesList> page =
                    customRepository.findByExample(
                            SpeciesList.builder().title("THREATENED").build(), null, PageRequest.of(0, 10));

            assertEquals(1, page.getTotalElements());
            assertEquals("dr400", page.getContent().get(0).getId());
        }

        @Test
        void findByExample_searchQueryLooksInTitleAndDescription() {
            saved("dr500", "dr500-uid", "Wattles", "Acacia of the Canberra region");
            saved("dr501", "dr501-uid", "Acacia list", "wattles");
            saved("dr502", "dr502-uid", "Orchids", "orchids of the region");

            Page<SpeciesList> page =
                    customRepository.findByExample(
                            SpeciesList.builder().build(), "acacia", PageRequest.of(0, 10));

            assertEquals(2, page.getTotalElements());
        }

        @Test
        void findByExample_searchQueryTreatsRegexCharactersAsText() {
            saved("dr600", "dr600-uid", "Acacia (sens. lat.)", null);
            saved("dr601", "dr601-uid", "Acacia sensu lato", null);

            // Pattern.quote in the repository is what keeps "(" and "." from being a regex: without
            // it this query is either an error or matches the second list as well
            Page<SpeciesList> page =
                    customRepository.findByExample(
                            SpeciesList.builder().build(), "Acacia (sens. lat.)", PageRequest.of(0, 10));

            assertEquals(1, page.getTotalElements());
            assertEquals("dr600", page.getContent().get(0).getId());
        }

        @Test
        void findByExample_countsEveryMatchWhileReturningOnePage() {
            for (int i = 0; i < 5; i++) {
                saved("dr70" + i, "dr70" + i + "-uid", "Paged list " + i, null);
            }

            Page<SpeciesList> page =
                    customRepository.findByExample(
                            SpeciesList.builder().title("Paged list").build(), null, PageRequest.of(1, 2));

            // The count query drops the page's limit and skip; the content keeps them
            assertEquals(5, page.getTotalElements());
            assertEquals(2, page.getContent().size());
            assertEquals(3, page.getTotalPages());
            assertEquals("dr702", page.getContent().get(0).getId());
        }

        @Test
        void findByMultipleExamples_returnsListsMatchingEitherExample() {
            saved("dr800", "dr800-uid", "Public list", null);
            listRepository.save(
                    SpeciesList.builder()
                            .id("dr801")
                            .dataResourceUid("dr801-uid")
                            .title("Private list")
                            .isPrivate(true)
                            .owner("alice")
                            .build());
            listRepository.save(
                    SpeciesList.builder()
                            .id("dr802")
                            .dataResourceUid("dr802-uid")
                            .title("Someone else's private list")
                            .isPrivate(true)
                            .owner("bob")
                            .build());

            // How the API shows a signed-in user the public lists plus their own private ones
            Page<SpeciesList> page =
                    customRepository.findByMultipleExamples(
                            SpeciesList.builder().isPrivate(false).build(),
                            SpeciesList.builder().isPrivate(true).owner("alice").build(),
                            PageRequest.of(0, 10));

            assertEquals(2, page.getTotalElements());
            assertTrue(page.getContent().stream().noneMatch(list -> "dr802".equals(list.getId())));
        }

        @Test
        void findByExample_ignoresNullFields() {
            saved("dr900", "dr900-uid", "Only list", null);

            // Every field null means no criteria at all, which is how the unfiltered browse works
            Optional<SpeciesList> only =
                    customRepository
                            .findByExample(SpeciesList.builder().build(), null, PageRequest.of(0, 10))
                            .getContent()
                            .stream()
                            .findFirst();

            assertTrue(only.isPresent());
            assertEquals("dr900", only.get().getId());
        }
    }
}
