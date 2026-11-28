package com.dayo.capstone.cadence.notification.service;

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;

/** Exposes WebPushCrypto's package-private pieces to tests in other packages. */
public final class WebPushCryptoTestAccess {
    private WebPushCryptoTestAccess() {}

    public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] auth, KeyPair ephemeral, byte[] salt) {
        return WebPushCrypto.encrypt(payload, uaPublic, auth, ephemeral, salt);
    }

    public static ECPublicKey publicKey(byte[] uncompressed) {
        return WebPushCrypto.publicKey(uncompressed);
    }

    public static byte[] uncompressed(ECPublicKey key) {
        return WebPushCrypto.uncompressed(key);
    }
}
