package com.dayo.capstone.cadence.security.service;

import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.security.config.JwtProperties;
import com.dayo.capstone.cadence.security.dto.response.AuthResponse;
import com.dayo.capstone.cadence.security.model.RefreshToken;
import com.dayo.capstone.cadence.security.repository.RefreshTokenRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Issues access + refresh tokens for one (user, institution) session. Every
 * sign-in path goes through here — password login, biometric login, refresh,
 * switching institution, institution sign-up and invite acceptance — so the
 * rule for which institution a session may act in lives in one place:
 * an active membership, or (only) a platform admin entering any institution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTokenService {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    static final String UNAVAILABLE = "This institution isn't available right now.";

    /**
     * Where a fresh login lands: the last institution used, else the oldest
     * membership — only among usable (ACTIVE) institutions. Someone whose
     * every institution is pending or suspended can't sign in; a platform
     * admin always can, into the platform console (no institution).
     */
    public Institution defaultInstitutionFor(User user) {
        Institution last = user.getLastActiveInstitution();
        if (last != null && last.isUsable() && activeMembership(user, last) != null) {
            return last;
        }
        boolean anyMembership = user.getMemberships().stream().anyMatch(Membership::isActive);
        Institution first = user.getMemberships().stream()
                .filter(Membership::isActive)
                .filter(m -> m.getInstitution().isUsable())
                .min(Comparator.comparing(Membership::getCreatedAt))
                .map(Membership::getInstitution)
                .orElse(null);
        if (first == null && !user.isPlatformAdmin()) {
            throw new BadCredentialsException(anyMembership
                    ? UNAVAILABLE + " Contact your institution or Cadence support."
                    : "Your account isn't part of any institution yet.");
        }
        return first;
    }

    @Transactional
    public AuthResponse issue(User user, Institution institution, String userAgent, String ipAddress) {
        Set<String> roles = rolesIn(user, institution);

        // Revoke any existing active session from this same browser/device before
        // issuing a new one — prevents unbounded accumulation of "Active" devices
        // every time the same browser logs in again.
        refreshTokenRepository.findByUserAndUserAgentAndRevokedFalse(user, userAgent)
                .forEach(existing -> {
                    existing.setRevoked(true);
                    existing.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(existing);
                });

        String rawRefreshToken = jwtService.generateRefreshTokenRaw();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setInstitution(institution);
        refreshToken.setTokenHash(jwtService.hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpiryMs() / 1000));
        refreshToken.setUserAgent(userAgent);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);

        if (institution != null) {
            user.setLastActiveInstitution(institution);
            userRepository.save(user);
        }

        return response(user, institution, roles, jwtService.generateAccessToken(
                user.getPublicId(), roles, institution != null ? institution.getPublicId() : null, user.isPlatformAdmin()),
                rawRefreshToken);
    }

    /** New access token for the same session, after re-checking the user may still act in its institution. */
    @Transactional
    public AuthResponse refresh(RefreshToken storedToken, String rawRefreshToken) {
        User user = storedToken.getUser();
        Institution institution = storedToken.getInstitution();
        Set<String> roles;
        try {
            roles = rolesIn(user, institution);
        } catch (AccessDeniedException e) {
            // Removed or suspended since this session began — end it.
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(storedToken);
            throw new BadCredentialsException("Your access to this institution has changed. Please sign in again.");
        }
        storedToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(storedToken);
        return response(user, institution, roles, jwtService.generateAccessToken(
                user.getPublicId(), roles, institution != null ? institution.getPublicId() : null, user.isPlatformAdmin()),
                rawRefreshToken);
    }

    /**
     * The caller's roles in the given institution: their active membership's
     * roles. Anyone without one is refused — platform admins included; this is
     * the tenant boundary. Only a platform admin may hold a session in no
     * institution (the platform console).
     */
    public Set<String> rolesIn(User user, Institution institution) {
        if (institution == null) {
            if (!user.isPlatformAdmin()) {
                throw new AccessDeniedException("Choose an institution you belong to");
            }
            return Set.of();
        }
        // Pending or suspended: closed to members (sign-in, switching in,
        // accepting an invite, refreshing a session).
        if (!institution.isUsable()) {
            throw new AccessDeniedException(UNAVAILABLE);
        }
        Membership membership = activeMembership(user, institution);
        if (membership != null) {
            return membership.getRoles().stream().map(Role::name).collect(Collectors.toCollection(TreeSet::new));
        }
        throw new AccessDeniedException("You are not a member of this institution");
    }

    private Membership activeMembership(User user, Institution institution) {
        return user.membershipIn(institution.getId()).filter(Membership::isActive).orElse(null);
    }

    private AuthResponse response(User user, Institution institution, Set<String> roles, String accessToken, String rawRefreshToken) {
        return AuthResponse.builder()
                .userPublicId(user.getPublicId())
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .roles(roles)
                .institutionPublicId(institution != null ? institution.getPublicId() : null)
                .institutionName(institution != null ? institution.getName() : null)
                .institutionSlug(institution != null ? institution.getSlug() : null)
                .platformAdmin(user.isPlatformAdmin())
                .build();
    }
}
