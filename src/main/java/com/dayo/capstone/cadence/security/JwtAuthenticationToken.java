package com.dayo.capstone.cadence.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final CadencePrincipal principal;

    public JwtAuthenticationToken(CadencePrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // token itself never needs to be re-exposed post-verification
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}