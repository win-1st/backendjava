package com.tathang.example304.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.tathang.example304.model.Bill;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    Optional<Bill> findByOrder_Id(Long orderId);

    List<Bill> findByPaymentStatus(Bill.PaymentStatus paymentStatus);

    @Query("SELECT b FROM Bill b WHERE b.issuedAt BETWEEN :startDate AND :endDate")
    List<Bill> findByIssuedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT SUM(b.totalAmount) FROM Bill b WHERE b.paymentStatus = 'COMPLETED' AND b.issuedAt BETWEEN :startDate AND :endDate")
    Double getTotalRevenueByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    Optional<Bill> findByPayosOrderCode(Long payosOrderCode);

    Optional<Bill> findByOrderIdAndPaymentMethodAndPaymentStatus(
            Long orderId,
            Bill.PaymentMethod paymentMethod,
            Bill.PaymentStatus paymentStatus);

}