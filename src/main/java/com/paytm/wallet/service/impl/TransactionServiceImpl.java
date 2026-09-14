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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
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
            log.warn("event=idempotency_conflict idempotencyKey={} transactionId={} fromId={} toId={} amount={}",
                    idempotencyKey, transaction.getId(), request.getFromId(), request.getToId(), request.getAmount());
            throw new IdempotencyKeyConflictException(
                    "idempotencyKey " + idempotencyKey + " was already used with a different request");
        }

        if (transaction.getStatus() != TransactionStatus.IN_PROGRESS) {
            log.info("event=idempotent_replay transactionId={} idempotencyKey={} status={}",
                    transaction.getId(), idempotencyKey, transaction.getStatus());
        } else {
            log.info("event=transfer_created transactionId={} idempotencyKey={} fromId={} toId={} amount={}",
                    transaction.getId(), idempotencyKey, request.getFromId(), request.getToId(), request.getAmount());
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
            log.warn("event=transfer_declined reason=insufficient_balance fromId={} toId={} amount={} availableBalance={}",
                    request.getFromId(), request.getToId(), request.getAmount(), fromWallet.getBalance());
            throw new InsufficientBalanceException("No minimum balance for wallet with ID :: " + request.getFromId());
        }

        fromWallet.setBalance(fromWallet.getBalance() - request.getAmount());
        toWallet.setBalance(toWallet.getBalance() + request.getAmount());
        walletRepository.saveAllAndFlush(List.of(fromWallet, toWallet));

        log.info("event=wallet_debited walletId={} amount={} newBalance={}",
                fromWallet.getId(), request.getAmount(), fromWallet.getBalance());
        log.info("event=wallet_credited walletId={} amount={} newBalance={}",
                toWallet.getId(), request.getAmount(), toWallet.getBalance());
    }

    @Override
    @Transactional
    public Transaction saveTransactionForStatus(Transaction transaction, TransactionStatus status) {
        transaction.setStatus(status);
        Transaction saved = transactionRepository.saveAndFlush(transaction);
        log.info("event=transfer_status_updated transactionId={} status={}", saved.getId(), status);
        return saved;
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
