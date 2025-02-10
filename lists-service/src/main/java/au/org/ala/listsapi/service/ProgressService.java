package au.org.ala.listsapi.service;

import au.org.ala.listsapi.model.MigrateProgressItem;
import au.org.ala.listsapi.model.SpeciesList;
import au.org.ala.listsapi.repo.IngestProgressMongoRepository;
import au.org.ala.listsapi.repo.MigrateProgressMongoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import au.org.ala.listsapi.model.IngestProgressItem;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ProgressService {
    @Autowired protected IngestProgressMongoRepository ingestProgressMongoRepository;
    @Autowired protected MigrateProgressMongoRepository migrateProgressMongoRepository;

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
        IngestProgressItem item = new IngestProgressItem(speciesListID, rowCount);
        ingestProgressMongoRepository.save(item);
    }

    public void addIngestMongoProgress(String speciesListId, long count) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListId);
        if (item.isPresent()) {
            IngestProgressItem currentItem = item.get();
            currentItem.setMongoTotal(currentItem.getMongoTotal() + count);

            ingestProgressMongoRepository.save(currentItem);
        }
    }

    public void addIngestElasticProgress(String speciesListId, long count) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListID(speciesListId);
        if (item.isPresent()) {
            IngestProgressItem currentItem = item.get();
            currentItem.setElasticTotal(currentItem.getElasticTotal() + count);

            ingestProgressMongoRepository.save(currentItem);
        }
    }
}
