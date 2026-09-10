package com.pawtrack.backend.cat.service;

import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.api.dto.CatDetailResponse;
import com.pawtrack.backend.cat.api.mapper.CatMapper;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.repo.CatRepository;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class CatService {

    private final CatRepository catRepository;
    private final HealthDataRepository healthDataRepository;
    private final AlertRepository alertRepository;

    private final CatImageStorage images;

    public CatService(
            CatRepository catRepository,
            HealthDataRepository healthDataRepository,
            AlertRepository alertRepository,
            CatImageStorage images
    ) {
        this.catRepository = catRepository;
        this.healthDataRepository = healthDataRepository;
        this.alertRepository = alertRepository;
        this.images = images;
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

    public CatDetailResponse getDashboard(Long id) {
        Cat cat = getById(id);
        Optional<HealthData> latest = healthDataRepository.findFirstByCatIdOrderByTsDescIdDesc(id);
        BigDecimal temperatureC = latest.map(HealthData::getTemperatureC).orElse(null);
        boolean hasActiveAlert = alertRepository.existsByCatIdAndStatus(id, AlertStatus.OPEN);
        return CatMapper.toDetailResponse(cat, temperatureC, hasActiveAlert);
    }

    @Transactional
    public Cat uploadCatImage(Long id, MultipartFile file) {
        Cat cat = getForUpdate(id);
        cat.setImageUrl(images.store(file));
        return catRepository.save(cat);
    }
}
