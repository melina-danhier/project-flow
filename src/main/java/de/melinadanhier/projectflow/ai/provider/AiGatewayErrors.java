package de.melinadanhier.projectflow.ai.provider;

import de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import static de.melinadanhier.projectflow.ai.exception.AiTechnicalErrorCode.*;

/** Gemeinsame Fehlerklassifikation ohne Abhängigkeit von einem Provider-SDK. */
public final class AiGatewayErrors {

    private AiGatewayErrors() {}

    public static AiTechnicalErrorCode errorCodeForStatusCode(int status) {
        if (status == 408 || status == 504) {
            return PROVIDER_TIMEOUT;
        }
        if (status == 429) {
            return RATE_LIMIT_EXCEEDED;
        }
        if (status >= 500 && status < 600) {
            return PROVIDER_UNAVAILABLE;
        }
        if (status >= 400 && status < 500) {
            return CLIENT_CONFIGURATION_ERROR;
        }
        return UNKNOWN_AI_ERROR;
    }

    public static boolean isTimeout(Throwable cause) {
        if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
            return true;
        }
        return cause instanceof InterruptedIOException && !Thread.currentThread().isInterrupted();
    }
}
