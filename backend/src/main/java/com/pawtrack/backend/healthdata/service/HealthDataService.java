package com.pawtrack.backend.healthdata.service;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.repo.AlertRepository;
import com.pawtrack.backend.cat.domain.Cat;
import com.pawtrack.backend.cat.service.CatService;
import com.pawtrack.backend.healthdata.domain.HealthData;
import com.pawtrack.backend.healthdata.repo.HealthDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class HealthDataService {

    private final CatService catService;
    private final HealthDataRepository healthDataRepository;
    private final AlertRepository alertRepository;
    private final HealthMonitorService healthMonitorService;

    public HealthDataService(
            CatService catService,
            HealthDataRepository healthDataRepository,
            AlertRepository alertRepository,
            HealthMonitorService healthMonitorService
    ) {
        this.catService = catService;
        this.healthDataRepository = healthDataRepository;
        this.alertRepository = alertRepository;
        this.healthMonitorService = healthMonitorService;
    }

    @Transactional
    public HealthData create(Long catId, OffsetDateTime ts, BigDecimal temperatureC, Integer activityLevel) {
        Cat cat = catService.getForUpdate(catId);

        HealthData hd = new HealthData();
        hd.setCat(cat);
        hd.setTs(ts != null ? ts : OffsetDateTime.now());
        hd.setTemperatureC(temperatureC);
        hd.setActivityLevel(activityLevel);

        HealthData saved = healthDataRepository.save(hd);
        healthMonitorService.analyzeAndAlert(catId);

        return saved;
    }

    public List<HealthData> listByCat(Long catId) {
        catService.getById(catId);
        return healthDataRepository.findByCatIdOrderByTsDescIdDesc(catId);
    }


    public List<Alert> listAlertsByCat(Long catId) {
        catService.getById(catId);
        return alertRepository.findByCatIdWithCat(catId);
    }
}
