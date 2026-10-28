package com.example.mini_nanofaas;

/**
 * Eccezione custom usata per segnalare a Spring Retry che il container
 * della funzione non è ancora pronto a ricevere traffico (endpoint /health non risponde o errore).
 * Il framework cattura questa eccezione e attiva automaticamente un nuovo tentativo di retry.
 */
public class ContainerNotReadyException extends RuntimeException {

    public ContainerNotReadyException(String message) {
        super(message);
    }
}
