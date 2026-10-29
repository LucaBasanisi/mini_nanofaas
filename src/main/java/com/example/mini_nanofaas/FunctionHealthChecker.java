package com.example.mini_nanofaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Servizio dedicato alla verifica dello stato di salute (Readiness Check) dei container delle funzioni.
 *
 * Utilizza Spring Retry: invece di avere cicli bloccanti con 'while' e 'Thread.sleep()',
 * il metodo 'checkReadiness' dichiara tramite annotazioni la politica di tentativi e backoff.
 * Se l'endpoint /health fallisce, viene sollevata un'eccezione {@link ContainerNotReadyException}
 * che istruisce Spring a riprovare dopo un intervallo di tempo prestabilito.
 */
@Service
public class FunctionHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(FunctionHealthChecker.class);

    private final RestClient restClient;

    public FunctionHealthChecker(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /**
     * Esegue il controllo di salute dell'endpoint HTTP '/health' del servizio Docker.
     *
     * In caso di insuccesso (container ancora in fase di boot, rete non ancora agganciata, ecc.),
     * il metodo lancia {@link ContainerNotReadyException}. Spring Retry intercetta tale eccezione
     * e ripete la chiamata:
     * - retryFor: le eccezioni che scatenano un nuovo tentativo.
     * - maxAttempts: numero massimo di tentativi prima di dichiarare il container non pronto.
     * - backoff: intervallo di attesa tra un tentativo e il successivo (delay = 500 ms).
     *
     * @param functionName Nome della funzione da verificare
     * @return true se il container ha risposto con codice HTTP 2xx
     */
    @Retryable(
            retryFor = { ContainerNotReadyException.class },
            maxAttempts = 10,
            backoff = @Backoff(delay = 500)
    )
    public boolean checkReadiness(String functionName) {
        String url = FunctionSpec.healthUrlFor(functionName);
        String serviceName = FunctionSpec.serviceNameFor(functionName);
        log.debug("Verifica readiness per la funzione '{}' su {}", functionName, url);

        try {
            // Effettuiamo la chiamata HTTP GET all'endpoint /health del container
            var response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Container '{}' è pronto e risponde correttamente (HTTP 2xx).", serviceName);
                return true;
            }
        } catch (Exception e) {
            // Qualsiasi errore di rete (es. Connection Refused o Timeout) indica
            // che il container sta ancora avviandosi.
            log.debug("Container '{}' non ancora raggiungibile: {}", serviceName, e.getMessage());
        }

        // Se non abbiamo ottenuto una risposta 2xx, lanciamo l'eccezione per attivare il retry di Spring
        throw new ContainerNotReadyException("Container per la funzione '" + functionName + "' non è ancora pronto.");
    }

    /**
     * Metodo di recupero (fallback) invocato automaticamente da Spring Retry
     * quando tutti i tentativi configurati in {@link #checkReadiness(String)} sono falliti.
     *
     * @param e L'eccezione catturata all'ultimo tentativo
     * @param functionName Il nome della funzione che non è riuscita ad avviarsi
     * @return false per indicare allo SwarmProvider che il deploy non è pronto
     */
    @Recover
    public boolean recover(ContainerNotReadyException e, String functionName) {
        log.warn("Readiness check fallito per '{}': superato il numero massimo di tentativi.", FunctionSpec.serviceNameFor(functionName));
        return false;
    }
}
