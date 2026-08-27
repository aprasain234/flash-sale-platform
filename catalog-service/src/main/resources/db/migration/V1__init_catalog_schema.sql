-- V1__init_catalog_schema.sql

CREATE TABLE venues (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name VARCHAR(255) NOT NULL,
                        city VARCHAR(255) NOT NULL,
                        capacity INT NOT NULL
);

CREATE TABLE events (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        venue_id UUID NOT NULL REFERENCES venues(id),
                        name VARCHAR(255) NOT NULL,
                        start_time TIMESTAMP WITH TIME ZONE NOT NULL,
                        status VARCHAR(50) NOT NULL -- e.g., SCHEDULED, CANCELED, COMPLETED
);

CREATE TABLE sections (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          venue_id UUID NOT NULL REFERENCES venues(id),
                          name VARCHAR(100) NOT NULL -- e.g., '101s', 'Floor', 'General Admission'
);

CREATE TABLE seats (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       section_id UUID NOT NULL REFERENCES sections(id),
                       row_name VARCHAR(10) NOT NULL,
                       seat_number INT NOT NULL,
                       tier VARCHAR(50) NOT NULL, -- e.g., STANDARD, VIP, PLATINUM
                       price DECIMAL(10, 2) NOT NULL
);

-- Indexes for read-heavy operations
CREATE INDEX idx_events_venue ON events(venue_id);
CREATE INDEX idx_sections_venue ON sections(venue_id);
CREATE INDEX idx_seats_section ON seats(section_id);