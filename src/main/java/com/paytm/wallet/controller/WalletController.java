package com.paytm.wallet.controller;

import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.request.WalletRequest;
import com.paytm.wallet.service.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public ResponseEntity<Integer> getBalance(@PathVariable String walletId) {
        log.info("event=api_request endpoint=GET_wallet_balance walletId={}", walletId);
        return ResponseEntity.ok(walletService.getBalance(walletId));
    }

    @PostMapping
    public ResponseEntity<Wallet> getOrCreateWallet(@RequestBody WalletRequest walletRequest) {
        log.info("event=api_request endpoint=POST_wallet userId={} amount={}",
                walletRequest.getUserId(), walletRequest.getAmount());
        return ResponseEntity.ok(walletService.getOrCreateWallet(walletRequest));
    }
}
