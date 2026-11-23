-- PostgreSQL only (H2 has no exclusion constraints).
-- Defense in depth for the overlap rule: the application already serializes bookings per room
-- with a pessimistic lock, but this makes double bookings impossible even for writes that bypass
-- the application (scripts, another service, a future bug).
-- Two ACTIVE reservations of the same room cannot have overlapping half-open ranges [start, end).
-- btree_gist is needed to combine "=" on room_id with "&&" (overlaps) on the range in one GiST index.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservations
    ADD CONSTRAINT ex_reservations_no_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'ACTIVE');
