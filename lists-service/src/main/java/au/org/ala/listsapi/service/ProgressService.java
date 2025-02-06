package au.org.ala.listsapi.service;

import au.org.ala.listsapi.repo.IngestProgressMongoRepository;
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
    private final long HALF_DAY_IN_MS = 43200000;

    @Scheduled(fixedRate = HALF_DAY_IN_MS)
    private void cullEmptyProgress() {
        Date HALF_DAY_OLD = new Date(System.currentTimeMillis() - HALF_DAY_IN_MS);
        ingestProgressMongoRepository.deleteIngestProgressItemsByStartedBefore(HALF_DAY_OLD);
    }

    public IngestProgressItem getMigrationProgress(String speciesListId) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListId(speciesListId);
        return item.orElseGet(() -> new IngestProgressItem(speciesListId, 0, 0));
    }

    public void clearIngestProgress(String speciesListId) {
        ingestProgressMongoRepository.deleteIngestProgressItemBySpeciesListId(speciesListId);
    }

    public void addIngestMongoProgress(String speciesListId, long progress) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListId(speciesListId);
        if (item.isPresent()) {
            IngestProgressItem currentItem = item.get();
            currentItem.setMongoProgress(currentItem.getMongoProgress() + progress);

            ingestProgressMongoRepository.save(currentItem);
        } else {
            ingestProgressMongoRepository.save(new IngestProgressItem(speciesListId, progress, 0));
        }
    }

    public void addIngestElasticProgress(String speciesListId, long progress) {
        Optional<IngestProgressItem> item = ingestProgressMongoRepository.findIngestProgressItemBySpeciesListId(speciesListId);
        if (item.isPresent()) {
            IngestProgressItem currentItem = item.get();
            currentItem.setElasticProgress(currentItem.getElasticProgress() + progress);

            ingestProgressMongoRepository.save(currentItem);
        }
    }
}
