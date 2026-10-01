package com.dayo.capstone.cadence.security.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebAuthnAuthenticationRequest {
    private String credentialId;
    private String authenticatorData;
    private String clientDataJSON;
    private String signature;
}