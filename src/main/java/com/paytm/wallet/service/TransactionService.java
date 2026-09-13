package com.paytm.wallet.service;

import com.paytm.wallet.constants.TransactionStatus;
import com.paytm.wallet.model.Transaction;
import com.paytm.wallet.model.request.TransactionRequest;

public interface TransactionService {

    TransactionStatus getTransactionStatus(String transactionId);

    void performTransaction(TransactionRequest request);

    void validateWallets(TransactionRequest request);

    Transaction saveTransactionForStatus(Transaction transaction, TransactionStatus inProgress);

    Transaction createOrGetExisting(TransactionRequest transactionRequest, String idempotencyKey);
}
