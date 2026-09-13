package com.paytm.wallet.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String userName;
    @Column(unique = true, nullable = false)
    private String token;
}
