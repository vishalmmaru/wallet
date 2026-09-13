package com.paytm.wallet.model.request;

import lombok.Data;

@Data
public class WalletRequest {

    private String userId;
    private int amount;
}
