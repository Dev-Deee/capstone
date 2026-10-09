package com.dayo.capstone.cadence.security;

import com.dayo.capstone.cadence.security.repository.BlacklistedTokenRepository;
import com.dayo.capstone.cadence.common.tenant.TenantContext;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.institution.repository.InstitutionRepository;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import com.dayo.capstone.cadence.security.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final InstitutionRepository institutionRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.verifyToken(token);
            String jti = jwtService.extractJti(claims);

            if (blacklistedTokenRepository.existsByJti(jti)) {
                log.warn("Rejected blacklisted token: jti={}", jti);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            UUID userPublicId = UUID.fromString(claims.getSubject());

            // Deactivated/deleted accounts lose access immediately, not when
            // their (up to 15-minute) access token happens to expire.
            User user = userRepository.findByPublicId(userPublicId)
                    .filter(u -> u.isActive() && !u.isDeleted())
                    .orElse(null);
            if (user == null) {
                log.warn("Rejected token for inactive or deleted user: userPublicId={}", userPublicId);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // Tenant scope. The token names the institution; the caller's roles
            // there are read fresh from their Membership, so demotion, suspension
            // or removal applies on the very next request. A platform admin is the
            // only caller who may act in an institution without a membership.
            String inst = claims.get("inst", String.class);
            Set<String> roles = new HashSet<>();
            Long institutionId = null;
            UUID institutionPublicId = null;
            boolean platformAdminActing = false;
            if (inst != null) {
                institutionPublicId = UUID.fromString(inst);
                Optional<Membership> membership = membershipRepository
                        .findByUserPublicIdAndInstitutionPublicId(userPublicId, institutionPublicId)
                        .filter(Membership::isActive);
                if (membership.isPresent() && !membership.get().getInstitution().isUsable() && !user.isPlatformAdmin()) {
                    // Pending or suspended since this token was issued: the caller stays
                    // signed in but acts in no institution — they can switch to another
                    // or sign out, and nothing here is reachable.
                    log.warn("Request in an unavailable institution: userPublicId={}, institution={}, status={}",
                            userPublicId, inst, membership.get().getInstitution().getStatus());
                    institutionPublicId = null;
                } else if (membership.isPresent()) {
                    institutionId = membership.get().getInstitution().getId();
                    membership.get().getRoles().forEach(r -> roles.add(r.name()));
                } else if (user.isPlatformAdmin()) {
                    institutionId = institutionRepository.findByPublicId(institutionPublicId)
                            .map(Institution::getId).orElse(null);
                    if (institutionId != null) {
                        roles.add(Role.SUPER_ADMIN.name());
                        platformAdminActing = true;
                    }
                }
                if (institutionId == null && institutionPublicId != null) {
                    log.warn("Rejected token for an institution the user no longer belongs to: userPublicId={}, institution={}",
                            userPublicId, inst);
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            CadencePrincipal principal = new CadencePrincipal(userPublicId, Set.copyOf(roles), institutionPublicId, user.isPlatformAdmin());

            List<GrantedAuthority> authorities = new ArrayList<>();
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            if (user.isPlatformAdmin()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
            }

            JwtAuthenticationToken authToken = new JwtAuthenticationToken(principal, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);
            TenantContext.set(new TenantContext.Scope(userPublicId, institutionId, institutionPublicId, platformAdminActing));
        } catch (Exception e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
