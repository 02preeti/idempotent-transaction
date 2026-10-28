
package com.transactionProcessor.controller;

import com.transactionProcessor.dto.TransactionRequest;
import com.transactionProcessor.model.Transaction;
import com.transactionProcessor.service.ProcessingSummary;
import com.transactionProcessor.service.TransactionService;
import com.transactionProcessor.worker.TransactionWorker;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService service;
    private final TransactionWorker worker;

    public TransactionController(
            TransactionService service,
            TransactionWorker worker) {
        this.service = service;
        this.worker = worker;
    }

    @PostMapping("/transactions")
    public ResponseEntity<Transaction> submit(
            @Valid @RequestBody TransactionRequest request) {
        Transaction result = service.submit(request.toEntity());

        HttpStatus status = result.getStatus().name().equals("DUPLICATE")
                ? HttpStatus.OK
                : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<Transaction> getById(
            @PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/transactions")
    public List<Transaction> getAll() {
        return service.findAll();
    }

    @GetMapping("/accounts/{accountId}/balance")
    public Map<String, Object> getBalance(
            @PathVariable String accountId) {
        BigDecimal balance = service.getBalance(accountId);
        return Map.of(
                "accountId", accountId,
                "currency", "INR",
                "balance", balance);
    }

    @PostMapping("/transactions/process")
    public ResponseEntity<Map<String, String>> triggerProcessing() {
        worker.poll();
        return ResponseEntity.accepted().body(
                Map.of("message", "Processing poll completed"));
    }

    @GetMapping("/transactions/health/summary")
    public ProcessingSummary getSummary() {
        return service.getSummary(0, 0, 0);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(
            IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(
                Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": "
                        + error.getDefaultMessage())
                .orElse("Invalid request");

        return ResponseEntity.badRequest().body(
                Map.of("error", message));
    }
}
