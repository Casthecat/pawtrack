package com.pawtrack.backend.cat.service;

import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.api.dto.CatDetailResponse;
import com.pawtrack.backend.cat.api.mapper.CatMapper;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.domain.CatAdoptionStatus;
import com.pawtrack.backend.cat.domain.CatHealthStatus;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class CatService {

    private final CatRepository catRepository;
    private final HealthDataRepository healthDataRepository;
    private final AlertRepository alertRepository;

    @Value("${upload.path:uploads/}")
    private String uploadPath;

    public CatService(
            CatRepository catRepository,
            HealthDataRepository healthDataRepository,
            AlertRepository alertRepository
    ) {
        this.catRepository = catRepository;
        this.healthDataRepository = healthDataRepository;
        this.alertRepository = alertRepository;
    }

    public Cat create(String name) {
        Cat cat = new Cat();
        cat.setName(name.trim());
        return catRepository.save(cat);
    }


    public List<Cat> list() {
        return catRepository.findAll();
    }


    public Cat getById(Long id) {
        return catRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat not found: " + id));
    }


    @Transactional
    public Cat getForUpdate(Long id) {
        return catRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat not found: " + id));
    }

    @Transactional
    public Cat updateStatus(Long id, String status) {
        Cat cat = getForUpdate(id);
        if (cat.getAdoptionStatus() == CatAdoptionStatus.ADOPTED || "ADOPTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adoption status is managed by the application review process.");
        }
        // Preserve the old request contract without permitting adoption-state writes.
        CatHealthStatus healthStatus = switch (status) {
            case "NORMAL", "ADOPTABLE" -> CatHealthStatus.NORMAL;
            case "UNDER_OBSERVATION" -> CatHealthStatus.UNDER_OBSERVATION;
            case "SICK" -> CatHealthStatus.SICK;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported cat status");
        };
        cat.setHealthStatus(healthStatus);
        return catRepository.save(cat);
    }

    public CatDetailResponse getDashboard(Long id) {
        Cat cat = getById(id);
        Optional<HealthData> latest = healthDataRepository.findFirstByCatIdOrderByTsDescIdDesc(id);
        BigDecimal temperatureC = latest.map(HealthData::getTemperatureC).orElse(null);
        boolean hasActiveAlert = alertRepository.existsByCatIdAndStatus(id, AlertStatus.OPEN);
        return CatMapper.toDetailResponse(cat, temperatureC, hasActiveAlert);
    }

    @Transactional
    public Cat uploadCatImage(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed");
        }

        Cat cat = getForUpdate(id);
        String extension = getFileExtension(file.getOriginalFilename());
        String filename = "cat_" + id + "_" + System.currentTimeMillis() + extension;
        Path uploadDir = Paths.get(uploadPath);
        Path target = uploadDir.resolve(filename);

        try {
            Files.createDirectories(uploadDir);
            file.transferTo(target.toFile());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file", ex);
        }

        String relativePath = normalizeRelativePath(uploadPath, filename);
        cat.setImageUrl(relativePath);
        return catRepository.save(cat);
    }

    private String getFileExtension(String filename) {
        if (filename == null) return ".jpg";
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return ".jpg";
        return filename.substring(idx);
    }

    private String normalizeRelativePath(String basePath, String filename) {
        if (basePath == null || basePath.isBlank()) {
            return filename;
        }
        String normalized = basePath.replace("\\", "/");
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized + filename;
    }
}
