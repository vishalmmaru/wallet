package com.paytm.wallet.controller;

import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.request.WalletRequest;
import com.paytm.wallet.service.WalletService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{walletId}")
    public ResponseEntity<Integer> getBalance(@PathVariable String walletId) {
        return ResponseEntity.ok(walletService.getBalance(walletId));
    }

    @PostMapping
    public ResponseEntity<Wallet> getOrCreateWallet(@RequestBody WalletRequest walletRequest) {
        return ResponseEntity.ok(walletService.getOrCreateWallet(walletRequest));
    }
}
