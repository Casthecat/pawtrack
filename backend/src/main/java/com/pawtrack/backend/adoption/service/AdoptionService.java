package com.pawtrack.backend.adoption.service;

import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationRequest;
import com.pawtrack.backend.adoption.api.dto.AdoptionApplicationResponse;
import com.pawtrack.backend.adoption.api.mapper.AdoptionApplicationMapper;
import com.pawtrack.backend.adoption.domain.AdoptionApplication;
import com.pawtrack.backend.adoption.domain.AdoptionStatus;
import com.pawtrack.backend.adoption.repo.AdoptionApplicationRepository;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdoptionService {
    private final AdoptionApplicationRepository applications;
    private final CatRepository cats;
    private final AlertRepository alerts;

    public List<AdoptionApplicationResponse> list(AdoptionStatus status) {
        return applications.findForReview(status).stream().map(AdoptionApplicationMapper::toResponse).toList();
    }

    public AdoptionApplicationResponse getById(Long id) {
        return response(findApplication(id));
    }

    @Transactional
    public AdoptionApplicationResponse submitApplication(AdoptionApplicationRequest req) {
        Cat cat = lockCat(req.getCatId());
        requireAvailable(cat);
        String email = req.getAdopterEmail().trim().toLowerCase(Locale.ROOT);
        if (applications.existsByCatIdAndAdopterEmailIgnoreCaseAndStatus(cat.getId(), email, AdoptionStatus.PENDING)) {
            throw conflict("You already have a pending application for this cat.");
        }
        AdoptionApplication app = new AdoptionApplication();
        app.setCat(cat);
        app.setAdopterName(req.getAdopterName().trim());
        app.setAdopterEmail(email);
        app.setNotes(req.getNotes() == null ? null : req.getNotes().trim());
        return response(applications.saveAndFlush(app));
    }

    @Transactional
    public AdoptionApplicationResponse approveApplication(Long id) {
        AdoptionApplication app = lockApplication(id);
        requirePending(app);
        requireAvailable(app.getCat());
        app.setStatus(AdoptionStatus.APPROVED);
        app.getCat().setStatus("ADOPTED");
        // Serialize decisions on a cat, including decisions on different applications.
        for (AdoptionApplication other : applications.findByCatIdAndStatus(app.getCat().getId(), AdoptionStatus.PENDING)) {
            if (!other.getId().equals(id)) other.setStatus(AdoptionStatus.REJECTED);
        }
        applications.flush();
        return response(app);
    }

    @Transactional
    public AdoptionApplicationResponse rejectApplication(Long id) {
        AdoptionApplication app = lockApplication(id);
        requirePending(app);
        app.setStatus(AdoptionStatus.REJECTED);
        applications.flush();
        return response(app);
    }

    private AdoptionApplication lockApplication(Long id) {
        Long catId = applications.findCatIdByApplicationId(id)
                .orElseThrow(() -> new EntityNotFoundException("Adoption application not found: " + id));
        // Read application state only AFTER taking the shared cat lock.
        lockCat(catId);
        return findApplication(id);
    }

    private Cat lockCat(Long id) {
        return cats.findByIdForUpdate(id).orElseThrow(() -> new EntityNotFoundException("Cat not found: " + id));
    }

    private AdoptionApplication findApplication(Long id) {
        return applications.findWithCatById(id)
                .orElseThrow(() -> new EntityNotFoundException("Adoption application not found: " + id));
    }

    private void requireAvailable(Cat cat) {
        if (!cat.isAvailableForAdoption() || alerts.existsByCatIdAndStatus(cat.getId(), AlertStatus.OPEN)) {
            throw conflict("This cat is currently unavailable for adoption. Please refresh the cat's profile.");
        }
    }

    private void requirePending(AdoptionApplication app) {
        if (app.getStatus() != AdoptionStatus.PENDING) {
            throw conflict("Only pending applications can be reviewed. Please refresh the review queue.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private AdoptionApplicationResponse response(AdoptionApplication app) {
        return AdoptionApplicationMapper.toResponse(app);
    }
}
