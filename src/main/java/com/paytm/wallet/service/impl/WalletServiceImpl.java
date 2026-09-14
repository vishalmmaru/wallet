package com.paytm.wallet.service.impl;

import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.exceptions.ResourceNotFoundException;
import com.paytm.wallet.model.request.WalletRequest;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.WalletService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    public Wallet getOrCreateWallet(WalletRequest walletRequest) {
        Optional<Wallet> existing = walletRepository.findByUserId(walletRequest.getUserId());
        if (existing.isPresent()) {
            log.info("event=wallet_get_existing walletId={} userId={}",
                    existing.get().getId(), walletRequest.getUserId());
            return existing.get();
        }

        try {
            Wallet created = walletRepository.saveAndFlush(new Wallet(walletRequest.getUserId(), walletRequest.getAmount()));
            log.info("event=wallet_created walletId={} userId={} initialBalance={}",
                    created.getId(), walletRequest.getUserId(), walletRequest.getAmount());
            return created;
        } catch (DataIntegrityViolationException e) {
            log.warn("event=wallet_create_race_lost userId={} falling back to existing wallet lookup",
                    walletRequest.getUserId());
            return walletRepository.findByUserId(walletRequest.getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Wallet creation failed for userId :: " + walletRequest.getUserId()));
        }
    }

    @Override
    public int getBalance(String walletId) {
        Optional<Wallet> walletOptional = walletRepository.findById(walletId);
        if (walletOptional.isEmpty()) {
            log.warn("event=wallet_not_found walletId={}", walletId);
            throw new ResourceNotFoundException("Wallet not found for ID :: " + walletId);
        }
        return walletOptional.get().getBalance();
    }
}
