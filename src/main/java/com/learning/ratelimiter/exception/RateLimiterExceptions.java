package com.learning.ratelimiter.exception;

public class RateLimiterExceptions {

        public static class RateLimiterException extends RuntimeException {
            public RateLimiterException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        public static class ConfigurationException extends RateLimiterException {
            public ConfigurationException(String message) {
                super(message, null);
            }
        }

        public static class RateLimiterUnavailableException extends RateLimiterException {
            public RateLimiterUnavailableException(String message, Throwable cause) {
                super(message, cause);
            }
        }

        public static class ClientDataException extends RateLimiterException {
            public ClientDataException(String message, Throwable cause) {
                super(message, cause);
            }
        }
}
