package com.fraud.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fraud.backend.api.AlertResponse;
import com.fraud.backend.service.FraudService;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final FraudService fraudService;

    public AlertController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @GetMapping
    public List<AlertResponse> listAlerts() {
        return fraudService.listAlerts();
    }
}
