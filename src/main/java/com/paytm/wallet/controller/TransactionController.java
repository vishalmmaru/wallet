package com.paytm.wallet.controller;

import com.paytm.wallet.constants.TransactionStatus;
import com.paytm.wallet.model.request.TransactionRequest;
import com.paytm.wallet.service.TransactionFacade;
import com.paytm.wallet.service.TransactionService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/transfers")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionFacade transactionFacade;

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionStatus> getTransactionStatus(@PathVariable String transactionId) {
        log.info("event=api_request endpoint=GET_transaction_status transactionId={}", transactionId);
        return ResponseEntity.ok(transactionService.getTransactionStatus(transactionId));
    }

    @PostMapping
    public ResponseEntity<String> makeTransfer(@RequestBody TransactionRequest transactionRequest,
                                               @RequestHeader(value = "idempotencyKey", required = false) String idempotencyKey) {
        log.info("event=api_request endpoint=POST_transfer fromId={} toId={} amount={} idempotencyKeyPresent={}",
                transactionRequest.getFromId(), transactionRequest.getToId(), transactionRequest.getAmount(),
                idempotencyKey != null && !idempotencyKey.isBlank());
        return ResponseEntity.ok(transactionFacade.performTransferOperations(transactionRequest, idempotencyKey));
    }
}
