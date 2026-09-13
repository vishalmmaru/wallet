package com.paytm.wallet.model;

import com.paytm.wallet.constants.TransactionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String fromId;
    private String toId;
    @Column(unique = true, nullable = false)
    private String idempotencyKey;
    private int amount;
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

}
