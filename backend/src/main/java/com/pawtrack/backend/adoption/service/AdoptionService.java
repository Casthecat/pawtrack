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
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.identity.repo.UserAccountRepository;
import com.pawtrack.backend.identity.security.AccountPrincipal;
import com.pawtrack.backend.identity.domain.UserRole;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdoptionService {
    private final AdoptionApplicationRepository applications;
    private final CatRepository cats;
    private final AlertRepository alerts;
    private final UserAccountRepository accounts;

    public List<AdoptionApplicationResponse> list(AdoptionStatus status) {
        return applications.findForReview(status).stream().map(AdoptionApplicationMapper::toResponse).toList();
    }

    public AdoptionApplicationResponse getById(Long id, AccountPrincipal principal) {
        requireAuthenticated(principal);
        var app = findApplication(id);
        if (principal.getRole() != UserRole.STAFF && (app.getAdopterAccount() == null
                || !app.getAdopterAccount().getId().equals(principal.getAccountId()))) {
            throw new EntityNotFoundException("Adoption application not found: " + id);
        }
        return response(app);
    }

    public List<AdoptionApplicationResponse> mine(AccountPrincipal principal) {
        requireAdopter(principal);
        return applications.findForOwner(principal.getAccountId()).stream()
                .map(AdoptionApplicationMapper::toResponse).toList();
    }

    @Transactional
    public AdoptionApplicationResponse submitApplication(AdoptionApplicationRequest req, AccountPrincipal principal) {
        requireAdopter(principal);
        var account = accounts.findById(principal.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
        if (account.getRole() != UserRole.ADOPTER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Adopter access required.");
        Cat cat = lockCat(req.getCatId());
        requireAvailable(cat);
        if (applications.existsByCatIdAndAdopterAccountIdAndStatus(cat.getId(), account.getId(), AdoptionStatus.PENDING)) {
            throw conflict("You already have a pending application for this cat.");
        }
        AdoptionApplication app = new AdoptionApplication();
        app.setCat(cat);
        app.setAdopterAccount(account);
        app.setAdopterName(account.getDisplayName());
        app.setAdopterEmail(account.getEmail());
        app.setNotes(req.getNotes() == null ? null : req.getNotes().trim());
        return response(applications.saveAndFlush(app));
    }

    @Transactional
    public AdoptionApplicationResponse approveApplication(Long id) {
        AdoptionApplication app = lockApplication(id);
        requirePending(app);
        requireAvailable(app.getCat());
        app.setStatus(AdoptionStatus.APPROVED);
        app.getCat().setAdoptionStatus(CatAdoptionStatus.ADOPTED);
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

    private void requireAuthenticated(AccountPrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    private void requireAdopter(AccountPrincipal principal) {
        requireAuthenticated(principal);
        if (principal.getRole() != UserRole.ADOPTER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Adopter access required.");
    }
}
