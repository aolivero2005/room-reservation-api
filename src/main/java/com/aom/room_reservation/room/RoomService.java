package com.aom.room_reservation.room;

import com.aom.room_reservation.common.ConflictException;
import com.aom.room_reservation.common.ConstraintViolations;
import com.aom.room_reservation.common.ResourceNotFoundException;
import com.aom.room_reservation.room.dto.CreateRoomRequest;
import com.aom.room_reservation.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    /** Unique constraint on rooms.name (V1 migration). */
    static final String UNIQUE_NAME_CONSTRAINT = "uk_rooms_name";

    private final RoomRepository roomRepository;

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        String name = request.name().trim();
        if (roomRepository.existsByName(name)) {
            throw duplicateName(name);
        }
        try {
            // flush now so a concurrent insert with the same name fails here (unique constraint)
            // and is reported as 409 instead of a 500 at commit time
            Room saved = roomRepository.saveAndFlush(new Room(name, request.capacity()));
            return RoomResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolations.isViolationOf(e, UNIQUE_NAME_CONSTRAINT)) {
                throw duplicateName(name);
            }
            throw e; // any other violation is a bug: let it surface as a 500 (and be logged)
        }
    }

    @Transactional(readOnly = true)
    public Page<RoomResponse> findAll(Pageable pageable) {
        return roomRepository.findAll(pageable).map(RoomResponse::from);
    }

    @Transactional(readOnly = true)
    public RoomResponse findById(Long id) {
        return roomRepository.findById(id)
                .map(RoomResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Room", id));
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException("A room named '" + name + "' already exists");
    }
}
