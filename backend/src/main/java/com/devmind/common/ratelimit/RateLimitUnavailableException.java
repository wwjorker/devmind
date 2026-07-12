package com.devmind.common.ratelimit;

public class RateLimitUnavailableException extends RuntimeException {

    public RateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
