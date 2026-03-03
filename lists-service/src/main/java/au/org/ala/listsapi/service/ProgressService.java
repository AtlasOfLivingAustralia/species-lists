package au.org.ala.listsapi.service;

import java.util.Date;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import au.org.ala.listsapi.model.IngestProgressItem;
import au.org.ala.listsapi.model.MigrateProgressItem;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.IngestProgressMongoRepository;
import au.org.ala.listsapi.repo.MigrateProgressMongoRepository;
import au.org.ala.listsapi.repo.SpeciesListMongoRepository;

@Service
public class ProgressService {
    private static final Logger logger = LoggerFactory.getLogger(ProgressService.class);
    @Autowired protected IngestProgressMongoRepository ingestProgressMongoRepository;
    @Autowired protected MigrateProgressMongoRepository migrateProgressMongoRepository;
    @Autowired protected SpeciesListMongoRepository speciesListMongoRepository;

    private final long HALF_DAY_IN_MS = 43200000;

    @Scheduled(fixedRate = HALF_DAY_IN_MS)
    private void cullEmptyProgress() {
        Date HALF_DAY_OLD = new Date(System.currentTimeMillis() - HALF_DAY_IN_MS);
        ingestProgressMongoRepository.deleteIngestProgressItemsByStartedBefore(HALF_DAY_OLD);
    }

    public void setupMigrationProgress(long total) {
        MigrateProgressItem item = new MigrateProgressItem(total);
        migrateProgressMongoRepository.save(item);
    }

    public void updateMigrationProgress(SpeciesList list) {
        Optional<MigrateProgressItem> optionalItem = migrateProgressMongoRepository.findById("_");
        if (optionalItem.isPresent()) {
            MigrateProgressItem item = optionalItem.get();

            item.setCurrentSpeciesList(list);
            item.setCompleted(item.getCompleted() + 1);

            migrateProgressMongoRepository.save(item);
        }
    }

    public MigrateProgressItem getMigrationProgress() {
        Optional<MigrateProgressItem> item = migrateProgressMongoRepository.findById("_");
        return item.orElse(null);
    }

    public void clearMigrationProgress() {
        migrateProgressMongoRepository.deleteById("_");
    }

    public IngestProgressItem getIngestProgress(String speciesListId) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListId);
        return item.orElseGet(() -> new IngestProgressItem(speciesListId, 0));
    }

    public void clearIngestProgress(String speciesListID) {
        ingestProgressMongoRepository.deleteIngestProgressItemBySpeciesListID(speciesListID);
    }

    public void setupIngestProgress(String speciesListID, long rowCount) {
        Optional<IngestProgressItem> existingItem = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListID);
        if (existingItem.isPresent()) {
            IngestProgressItem item = existingItem.get();
            item.setRowCount(rowCount);
            item.setMongoTotal(0);
            item.setElasticTotal(0);
            item.setCompleted(false);
            ingestProgressMongoRepository.save(item);
        } else {
            IngestProgressItem newItem = new IngestProgressItem(speciesListID, rowCount);
            ingestProgressMongoRepository.save(newItem);
        }
    }

    public void addIngestMongoProgress(String speciesListId, long count) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListId);
        if (item.isPresent()) {
            IngestProgressItem currentItem = item.get();
            currentItem.setMongoTotal(currentItem.getMongoTotal() + count);

            ingestProgressMongoRepository.save(currentItem);
        } else {
            Optional<SpeciesList> optionalSpeciesList = speciesListMongoRepository.findByIdOrDataResourceUid(speciesListId, speciesListId);
            if (optionalSpeciesList.isPresent()) {
                SpeciesList list = optionalSpeciesList.get();
                IngestProgressItem newItem = new IngestProgressItem(list.getId(), list.getRowCount(), count, 0);
                ingestProgressMongoRepository.save(newItem);
            }
        }
    }

    public void addIngestElasticProgress(String speciesListId, long count) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListId);
        if (item.isPresent()) {
            IngestProgressItem currentItem = item.get();
            currentItem.setElasticTotal(currentItem.getElasticTotal() + count);

            if (currentItem.getElasticTotal() == currentItem.getRowCount()) {
                currentItem.setCompleted(true);
            }

            ingestProgressMongoRepository.save(currentItem);
        }
    }

    public void resetIngestProgress(String speciesListId) {
        try {
            Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListId);
            if (item.isPresent()) {
                IngestProgressItem currentItem = item.get();
                currentItem.setMongoTotal(0);
                currentItem.setElasticTotal(0);

                ingestProgressMongoRepository.save(currentItem);
            } else {
                logger.warn("No ingest progress found to reset for speciesListId " + speciesListId);
            }
        } catch (Exception e) {
            // Log the exception or handle it as needed
            logger.error("Error resetting ingest progress for speciesListId " + speciesListId, e);
        }
    }
}
