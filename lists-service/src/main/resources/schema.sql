-- Enable Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Cleanup (for development iteration)
-- DROP TABLE IF EXISTS species_list_item;
-- DROP TABLE IF EXISTS species_list;
-- DROP TABLE IF EXISTS ingest_progress;
-- DROP TABLE IF EXISTS release;
-- DROP TABLE IF EXISTS migration_progress;

-- Species List Table
CREATE TABLE IF NOT EXISTS species_list (
    id VARCHAR(255) PRIMARY KEY, -- Keeping as String/VARCHAR to match current UUID usage if strict UUID type is not enforced in app
    version INTEGER,
    data_resource_uid VARCHAR(255),
    title TEXT,
    description TEXT,
    list_type VARCHAR(50),
    licence VARCHAR(255),
    original_field_list TEXT[], -- Array of strings
    field_list TEXT[],
    facet_list TEXT[],
    doi VARCHAR(255),
    row_count INTEGER,
    distinct_match_count BIGINT,
    authority VARCHAR(255),
    category VARCHAR(255),
    region VARCHAR(255),
    wkt GEOMETRY(Geometry, 4326),
    
    is_versioned BOOLEAN,
    is_authoritative BOOLEAN,
    is_private BOOLEAN,
    is_invasive BOOLEAN,
    is_threatened BOOLEAN,
    is_bie BOOLEAN,
    is_sds BOOLEAN,

    owner VARCHAR(255),
    owner_name VARCHAR(255),
    last_updated_by VARCHAR(255),
    editors TEXT[],
    approved_viewers TEXT[],
    tags TEXT[],
    
    classification JSONB,

    date_created TIMESTAMP,
    metadata_last_updated TIMESTAMP,
    last_updated TIMESTAMP,
    last_uploaded TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_species_list_druid ON species_list(data_resource_uid);
CREATE INDEX IF NOT EXISTS idx_species_list_owner ON species_list(owner);

-- Species List Item Table
CREATE TABLE IF NOT EXISTS species_list_item (
    id VARCHAR(255) PRIMARY KEY, -- Keeping as String to minimize refactoring friction if ObjectId was converted to String
    version INTEGER,
    species_list_id VARCHAR(255) REFERENCES species_list(id) ON DELETE CASCADE,
    taxon_id VARCHAR(255),
    supplied_name TEXT,
    scientific_name TEXT,
    vernacular_name TEXT,
    kingdom VARCHAR(255),
    phylum VARCHAR(255),
    classs VARCHAR(255), -- 'class' is a reserved keyword
    "order" VARCHAR(255), -- 'order' is a reserved keyword
    family VARCHAR(255),
    genus VARCHAR(255),
    
    properties JSONB,
    classification JSONB,
    
    date_created TIMESTAMP,
    last_updated TIMESTAMP,
    last_updated_by VARCHAR(255),

    -- Search Vector (Generated Column)
    search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', 
            coalesce(scientific_name, '') || ' ' || 
            coalesce(vernacular_name, '') || ' ' || 
            coalesce(supplied_name, '') || ' ' ||
            coalesce(properties::text, '')
        )
    ) STORED
);

CREATE INDEX IF NOT EXISTS idx_item_list_id ON species_list_item(species_list_id);
CREATE INDEX IF NOT EXISTS idx_item_scientific_name ON species_list_item USING GIN (scientific_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_item_properties ON species_list_item USING GIN (properties);
CREATE INDEX IF NOT EXISTS idx_item_search_vector ON species_list_item USING GIN (search_vector);

-- Ingest Progress Table (replacing Mongo collection)
CREATE TABLE IF NOT EXISTS ingest_progress (
    id VARCHAR(255) PRIMARY KEY,
    species_list_id VARCHAR(255),
    data_resource_uid VARCHAR(255),
    status VARCHAR(50),
    row_count BIGINT,
    mongo_total BIGINT,
    elastic_total BIGINT,
    completed BOOLEAN,
    processed_count BIGINT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message TEXT
);

-- Release Table (replacing Mongo collection)
CREATE TABLE IF NOT EXISTS release (
    id VARCHAR(255) PRIMARY KEY,
    species_list_id VARCHAR(255),
    stored_location VARCHAR(255),
    data_resource_uid VARCHAR(255),
    created_date TIMESTAMP,
    last_modified_date TIMESTAMP,
    released_version INTEGER,
    metadata JSONB
);

-- Migration Progress Table
CREATE TABLE IF NOT EXISTS migration_progress (
    id VARCHAR(255) PRIMARY KEY,
    current_species_list JSONB,
    completed BIGINT,
    total BIGINT,
    started TIMESTAMP
);
