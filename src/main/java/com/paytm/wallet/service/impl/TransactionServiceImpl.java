package com.paytm.wallet.service.impl;

import com.paytm.wallet.constants.TransactionStatus;
import com.paytm.wallet.model.Transaction;
import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.exceptions.IdempotencyKeyConflictException;
import com.paytm.wallet.model.exceptions.InsufficientBalanceException;
import com.paytm.wallet.model.exceptions.ResourceNotFoundException;
import com.paytm.wallet.model.request.TransactionRequest;
import com.paytm.wallet.repository.TransactionRepository;
import com.paytm.wallet.repository.WalletRepository;
import com.paytm.wallet.service.TransactionService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Override
    public TransactionStatus getTransactionStatus(String transactionId) {
        Optional<Transaction> optionalTransaction = transactionRepository.findById(transactionId);
        if (optionalTransaction.isEmpty()) throw new ResourceNotFoundException("Transaction not found for ID :: " + transactionId);
        return optionalTransaction.get().getStatus();
    }

    @Override
    @Transactional
    public Transaction createOrGetExisting(TransactionRequest request, String idempotencyKey) {
        transactionRepository.upsertTransaction(
                UUID.randomUUID().toString(), idempotencyKey,
                request.getFromId(), request.getToId(), request.getAmount(),
                TransactionStatus.IN_PROGRESS.name());

        Transaction transaction = transactionRepository.findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Transaction missing for idempotencyKey :: " + idempotencyKey));

        if (!matchesRequest(transaction, request)) {
            throw new IdempotencyKeyConflictException(
                    "idempotencyKey " + idempotencyKey + " was already used with a different request");
        }

        return transaction;
    }

    private boolean matchesRequest(Transaction existing, TransactionRequest request) {
        return existing.getFromId().equals(request.getFromId())
                && existing.getToId().equals(request.getToId())
                && existing.getAmount() == request.getAmount();
    }

    @Override
    public void performTransaction(TransactionRequest request) {
        Map<String, Wallet> wallets = walletRepository
                .findAllByIdForUpdate(Stream.of(request.getFromId(), request.getToId()).sorted().toList())
                .stream()
                .collect(Collectors.toMap(Wallet::getId, w -> w));

        Wallet fromWallet = wallets.get(request.getFromId());
        Wallet toWallet = wallets.get(request.getToId());

        if (fromWallet.getBalance() < request.getAmount()) {
            throw new InsufficientBalanceException("No minimum balance for wallet with ID :: " + request.getFromId());
        }

        fromWallet.setBalance(fromWallet.getBalance() - request.getAmount());
        toWallet.setBalance(toWallet.getBalance() + request.getAmount());
        walletRepository.saveAllAndFlush(List.of(fromWallet, toWallet));
    }

    @Override
    @Transactional
    public Transaction saveTransactionForStatus(Transaction transaction, TransactionStatus status) {
        transaction.setStatus(status);
        return transactionRepository.saveAndFlush(transaction);
    }

    @Override
    public void validateWallets(TransactionRequest request) {
        validateWalletForId(request.getFromId());
        validateWalletForId(request.getToId());
    }

    private void validateWalletForId(String walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new ResourceNotFoundException("Wallet not found for ID :: " + walletId);
        }
    }
}
