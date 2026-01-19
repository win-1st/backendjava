package com.tathang.example304.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tathang.example304.model.ResetPasswordToken;
import com.tathang.example304.model.User;

public interface ResetPasswordTokenRepository extends JpaRepository<ResetPasswordToken, Long> {
    Optional<ResetPasswordToken> findByToken(String token);

    Optional<ResetPasswordToken> findByUser(User user);

}