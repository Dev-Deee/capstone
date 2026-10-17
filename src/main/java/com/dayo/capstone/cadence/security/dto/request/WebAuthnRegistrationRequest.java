
package com.dayo.capstone.cadence.security.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebAuthnRegistrationRequest {
    private String credentialId;
    private String attestationObject;
    private String clientDataJSON;
    private String deviceLabel;
}