package com.paytm.wallet.service.impl;

import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.exceptions.ResourceNotFoundException;
import com.paytm.wallet.model.request.WalletRequest;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.WalletService;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    public Wallet getOrCreateWallet(WalletRequest walletRequest) {
        Optional<Wallet> existing = walletRepository.findById(walletRequest.getUserId());
        if (existing.isPresent()) return existing.get();

        try {
            return walletRepository.saveAndFlush(new Wallet(walletRequest.getUserId(), walletRequest.getAmount()));
        } catch (DataIntegrityViolationException e) {
            return walletRepository.findByUserId(walletRequest.getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Wallet creation failed for userId :: " + walletRequest.getUserId()));
        }
    }

    @Override
    public int getBalance(String walletId) {
        Optional<Wallet> walletOptional = walletRepository.findById(walletId);
        if (walletOptional.isEmpty()) throw new ResourceNotFoundException("Wallet not found for ID :: " + walletId);
        return walletOptional.get().getBalance();
    }

}
