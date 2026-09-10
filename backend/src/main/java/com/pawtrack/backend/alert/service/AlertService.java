package com.pawtrack.backend.alert.service;

import com.pawtrack.backend.alert.domain.Alert;
import com.pawtrack.backend.alert.domain.AlertStatus;
import com.pawtrack.backend.alert.api.dto.AlertResponse;
import com.pawtrack.backend.alert.api.mapper.AlertMapper;
import com.pawtrack.backend.alert.repo.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public List<Alert> listByCatId(Long catId) {
        return alertRepository.findByCatIdWithCat(catId);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(AlertStatus status) {
        return alertRepository.findQueueByStatus(status).stream().map(AlertMapper::toResponse).toList();
    }
}
