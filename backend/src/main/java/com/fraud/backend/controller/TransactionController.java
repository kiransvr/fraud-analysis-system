package com.fraud.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fraud.backend.api.TransactionRequest;
import com.fraud.backend.api.TransactionResponse;
import com.fraud.backend.service.FraudService;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final FraudService fraudService;

    public TransactionController(FraudService fraudService) {
        this.fraudService = fraudService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@Valid @RequestBody TransactionRequest request) {
        return fraudService.createAndScoreTransaction(request);
    }

    @GetMapping("/{externalTransactionId}")
    public TransactionResponse getTransaction(@PathVariable String externalTransactionId) {
        return fraudService.getTransaction(externalTransactionId);
    }

    @GetMapping
    public List<TransactionResponse> listTransactions(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String riskLevel) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        int normalizedOffset = Math.max(0, offset);
        return fraudService.listTransactions(normalizedLimit, normalizedOffset, riskLevel);
    }
}
