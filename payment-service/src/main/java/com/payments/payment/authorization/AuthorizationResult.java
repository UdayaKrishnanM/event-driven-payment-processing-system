package com.payments.payment.authorization;

public record AuthorizationResult(boolean approved, String authCode, DeclineReason declineReason) {

    public static AuthorizationResult approve(String authCode) {
        return new AuthorizationResult(true, authCode, null);
    }

    public static AuthorizationResult decline(DeclineReason reason) {
        return new AuthorizationResult(false, null, reason);
    }
}
