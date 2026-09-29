package com.dayo.capstone.cadence.security.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import com.dayo.capstone.cadence.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per registered authenticator (a student may register a phone's
 * fingerprint AND a laptop's Face ID as two separate credentials — Section
 * 6.12 never assumed one device per student).
 */
@Entity
@Table(name = "webauthn_credential")
@Getter
@Setter
@NoArgsConstructor
public class WebAuthnCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "credential_id", nullable = false, unique = true, length = 1000)
    private String credentialId; // Base64URL-encoded

    @Lob
    @Column(name = "public_key", nullable = false)
    private String publicKeyCose; // Base64-encoded COSE public key

    @Column(name = "sign_count", nullable = false)
    private long signCount = 0;

    @Column(name = "device_label")
    private String deviceLabel; // e.g. "Chrome on Linux" — same friendly-name logic already built on the frontend
}