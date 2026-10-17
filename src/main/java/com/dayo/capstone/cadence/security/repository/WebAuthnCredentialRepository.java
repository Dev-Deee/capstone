package com.dayo.capstone.cadence.security.repository;

import com.dayo.capstone.cadence.security.model.WebAuthnCredential;
import com.dayo.capstone.cadence.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, Long> {
    List<WebAuthnCredential> findByUser(User user);
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    boolean existsByUser(User user);
}