package au.org.ala.listsapi.service;

import au.org.ala.listsapi.controller.GraphQLController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import au.org.ala.listsapi.model.IngestProgressItem;

import java.util.HashMap;

@Service
public class ProgressService {
    private final HashMap<String, IngestProgressItem> ingestion = new HashMap<>();
    private final long HALF_DAY_IN_MS = 43200000;

    @Scheduled(fixedRate = HALF_DAY_IN_MS)
    private void cullEmptyProgress() {
        long NOW = System.currentTimeMillis();
        ingestion.forEach((key, value) -> {
            // Any entries older than a day, remove
            if (value.getCreatedTimestamp() + HALF_DAY_IN_MS < NOW) {
                ingestion.remove(key);
            }
        });
    }

    public IngestProgressItem getProgress(String speciesListId) {
        return ingestion.getOrDefault(speciesListId, new IngestProgressItem(0, 0, System.currentTimeMillis()));
    }

    public void addMongoProgress(String speciesListId, long progress) {
        IngestProgressItem current = ingestion.getOrDefault(speciesListId, new IngestProgressItem(0,0, System.currentTimeMillis()));
        current.setMongoProgress(current.getMongoProgress() + progress);
        ingestion.put(speciesListId, current);
    }

    public void addElasticProgress(String speciesListId, long progress) {
        IngestProgressItem current = ingestion.getOrDefault(speciesListId, new IngestProgressItem(0,0, System.currentTimeMillis()));
        current.setElasticProgress(current.getElasticProgress() + progress);
        ingestion.put(speciesListId, current);
    }
}
