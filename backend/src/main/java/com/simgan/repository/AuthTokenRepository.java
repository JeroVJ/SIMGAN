package com.simgan.repository;

import com.simgan.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByToken(String token);

    List<AuthToken> findByGanaderoIdAndIsRevokedFalse(Long ganaderoId);

    List<AuthToken> findByGanaderoId(Long ganaderoId);

    void deleteByToken(String token);
}
