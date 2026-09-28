-- Esquema inicial del núcleo. Los IDs UUID se crearán en la aplicación.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'HOTEL_ADMIN', 'SUPER_ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = lower(email))
);

CREATE TABLE hotels (
    id UUID PRIMARY KEY,
    admin_user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    name VARCHAR(180) NOT NULL,
    description TEXT,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    hotel_id UUID NOT NULL REFERENCES hotels(id),
    number VARCHAR(40) NOT NULL,
    description TEXT,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    price_per_night NUMERIC(12, 2) NOT NULL CHECK (price_per_night > 0),
    currency CHAR(3) NOT NULL DEFAULT 'PEN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uq_rooms_hotel_number UNIQUE (hotel_id, number)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    guest_user_id UUID NOT NULL REFERENCES users(id),
    room_id UUID NOT NULL REFERENCES rooms(id),
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    guests INTEGER NOT NULL CHECK (guests > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    expires_at TIMESTAMPTZ,
    nightly_price_snapshot NUMERIC(12, 2) NOT NULL CHECK (nightly_price_snapshot > 0),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount > 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reservations_dates CHECK (check_out > check_in),
    CONSTRAINT ck_pending_has_expiry CHECK (status <> 'PENDING_PAYMENT' OR expires_at IS NOT NULL)
);

CREATE INDEX idx_reservations_room_dates ON reservations(room_id, check_in, check_out);
CREATE INDEX idx_reservations_guest ON reservations(guest_user_id, created_at DESC);

CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    external_reference VARCHAR(160) UNIQUE,
    provider_payment_id VARCHAR(160) UNIQUE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_attempts_reservation ON payment_attempts(reservation_id);

-- La exclusión de solapamientos con retenciones que vencen depende de la hora actual
-- y se controlará dentro de transacciones con bloqueo de la fila rooms al implementar reservas.
