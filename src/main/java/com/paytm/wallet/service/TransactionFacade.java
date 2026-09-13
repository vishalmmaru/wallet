package com.paytm.wallet.service;

import com.paytm.wallet.constants.TransactionStatus;
import com.paytm.wallet.model.Transaction;
import com.paytm.wallet.model.exceptions.InsufficientBalanceException;
import com.paytm.wallet.model.exceptions.MissingIdempotencyKeyException;
import com.paytm.wallet.model.request.TransactionRequest;
import io.micrometer.common.util.StringUtils;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@AllArgsConstructor
@Component
public class TransactionFacade {

    private final TransactionService transactionService;

    @Transactional
    public String performTransferOperations(TransactionRequest transactionRequest, String idempotencyKey) {
        if (StringUtils.isBlank(idempotencyKey)) throw new MissingIdempotencyKeyException("idempotencyKey header is required");

        transactionService.validateWallets(transactionRequest);
        Transaction transaction = transactionService.createOrGetExisting(transactionRequest, idempotencyKey);

        if (transaction.getStatus() == TransactionStatus.IN_PROGRESS) {
            try {
                transactionService.performTransaction(transactionRequest);
                transaction = transactionService.saveTransactionForStatus(transaction, TransactionStatus.SUCCESSFUL);
            } catch (InsufficientBalanceException ex) {
                transactionService.saveTransactionForStatus(transaction, TransactionStatus.FAILED);
                throw ex;
            }
        }

        return transaction.getStatus().name();
    }

}
