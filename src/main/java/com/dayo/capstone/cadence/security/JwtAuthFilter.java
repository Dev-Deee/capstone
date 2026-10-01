package com.dayo.capstone.cadence.security;

import com.dayo.capstone.cadence.security.repository.BlacklistedTokenRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final UserRepository userRepository;

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
            if (!userRepository.existsByPublicIdAndIsActiveTrueAndIsDeletedFalse(userPublicId)) {
                log.warn("Rejected token for inactive or deleted user: userPublicId={}", userPublicId);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            @SuppressWarnings("unchecked")
            List<String> roleClaims = claims.get("roles", List.class);
            Set<String> roles = roleClaims == null ? Set.of() : Set.copyOf(roleClaims);

            CadencePrincipal principal = new CadencePrincipal(userPublicId, roles);

            List<GrantedAuthority> authorities = new ArrayList<>();
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));

            JwtAuthenticationToken authToken = new JwtAuthenticationToken(principal, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}