package com.dayo.capstone.cadence.security.model;

import com.dayo.capstone.cadence.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Denylist for access tokens revoked before their natural expiry (logout,
 * forced revocation). Keyed by jti, not the full token — never store raw
 * tokens at rest, same principle as RefreshToken.tokenHash.
 *
 * A row only needs to exist until expiresAt passes — after that, the
 * token is unusable regardless, so a cleanup job can safely purge old rows.
 */
@Entity
@Table(name = "blacklisted_token")
@Getter
@Setter
@NoArgsConstructor
public class BlacklistedToken extends BaseEntity {

    private String jti;

    private LocalDateTime expiresAt;
}