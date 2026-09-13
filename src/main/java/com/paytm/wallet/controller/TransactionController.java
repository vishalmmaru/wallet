package com.paytm.wallet.controller;

import com.paytm.wallet.constants.TransactionStatus;
import com.paytm.wallet.model.request.TransactionRequest;
import com.paytm.wallet.service.TransactionFacade;
import com.paytm.wallet.service.TransactionService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/transfers")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionFacade transactionFacade;

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionStatus> getTransactionStatus(@PathVariable String transactionId) {
        return ResponseEntity.ok(transactionService.getTransactionStatus(transactionId));
    }

    @PostMapping
    public ResponseEntity<String> makeTransfer(@RequestBody TransactionRequest transactionRequest,
                                               @RequestHeader(value = "idempotencyKey", required = false) String idempotencyKey) {
        return ResponseEntity.ok(transactionFacade.performTransferOperations(transactionRequest, idempotencyKey));
    }
}
