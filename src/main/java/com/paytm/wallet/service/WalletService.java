package com.paytm.wallet.service;

import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.model.request.WalletRequest;

public interface WalletService {

    Wallet getOrCreateWallet(WalletRequest walletRequest);
    int getBalance(String walletId);
}
