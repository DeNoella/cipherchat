package com.cipherchat.dto;

import com.cipherchat.model.User;


public record UserSummary(String username, boolean hasPublicKey, String fingerprint) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getUsername(), user.hasPublicKey(), user.getKeyFingerprint());
    }
}
