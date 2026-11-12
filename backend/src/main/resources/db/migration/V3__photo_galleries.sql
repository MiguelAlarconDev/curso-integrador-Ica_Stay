-- Metadatos exclusivamente: los archivos se almacenaran fuera de PostgreSQL.
-- UUID generados por la aplicacion, conforme a V1.
CREATE TABLE hotel_photos (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    alt_text VARCHAR(500) NOT NULL DEFAULT '',
    position INTEGER NOT NULL,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hotel_photos_hotel FOREIGN KEY (hotel_id) REFERENCES hotels(id),
    CONSTRAINT uq_hotel_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_hotel_photos_storage_key CHECK (length(btrim(storage_key)) > 0 AND storage_key = btrim(storage_key)),
    CONSTRAINT ck_hotel_photos_mime_type CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_hotel_photos_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_hotel_photos_dimensions CHECK (width > 0 AND height > 0),
    -- 15 posiciones unicas: limita el total incluso con inserciones concurrentes.
    CONSTRAINT ck_hotel_photos_position CHECK (position BETWEEN 0 AND 14),
    CONSTRAINT uq_hotel_photos_position UNIQUE (hotel_id, position) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE UNIQUE INDEX uq_hotel_photos_cover ON hotel_photos(hotel_id) WHERE is_cover;
CREATE INDEX idx_hotel_photos_order ON hotel_photos(hotel_id, position, id);

CREATE TABLE room_photos (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    alt_text VARCHAR(500) NOT NULL DEFAULT '',
    position INTEGER NOT NULL,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_photos_room FOREIGN KEY (room_id) REFERENCES rooms(id),
    CONSTRAINT uq_room_photos_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_room_photos_storage_key CHECK (length(btrim(storage_key)) > 0 AND storage_key = btrim(storage_key)),
    CONSTRAINT ck_room_photos_mime_type CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_room_photos_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_room_photos_dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_room_photos_position CHECK (position BETWEEN 0 AND 9),
    CONSTRAINT uq_room_photos_position UNIQUE (room_id, position) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE UNIQUE INDEX uq_room_photos_cover ON room_photos(room_id) WHERE is_cover;
CREATE INDEX idx_room_photos_order ON room_photos(room_id, position, id);
