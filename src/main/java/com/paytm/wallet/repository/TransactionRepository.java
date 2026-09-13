package com.paytm.wallet.repository;

import com.paytm.wallet.model.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.idempotencyKey = :idempotencyKey")
    Optional<Transaction> findByIdempotencyKeyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    @Modifying
    @Query(value = """
    INSERT INTO transaction (id, idempotency_key, from_id, to_id, amount, status)
    VALUES (:id, :idempotencyKey, :fromId, :toId, :amount, :status)
    ON CONFLICT (idempotency_key) DO NOTHING
    """, nativeQuery = true)
    void upsertTransaction(@Param("id") String id, @Param("idempotencyKey") String idempotencyKey,
                           @Param("fromId") String fromId, @Param("toId") String toId,
                           @Param("amount") int amount, @Param("status") String status);
}
