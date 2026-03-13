package com.fraud.backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fraud.backend.api.PredictRequest;
import com.fraud.backend.api.PredictResponse;
import com.fraud.backend.service.FraudService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/predict")
public class PredictionController {

    private final FraudService fraudService;

    public PredictionController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @PostMapping
    public PredictResponse predict(@Valid @RequestBody PredictRequest request) {
        return fraudService.predict(request);
    }
}
