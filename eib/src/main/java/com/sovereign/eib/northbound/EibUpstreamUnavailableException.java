package com.sovereign.eib.northbound;

public class EibUpstreamUnavailableException extends RuntimeException {

    public EibUpstreamUnavailableException(String message) {
        super(message);
    }

    public EibUpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
