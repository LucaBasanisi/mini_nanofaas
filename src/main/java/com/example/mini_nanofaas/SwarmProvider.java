package com.example.mini_nanofaas;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Service
public class SwarmProvider {

    // Ho usato RestClient anche dentro il functionDispatcher
    // Serve a fare chiamate interne ai servizi dell'applicazione
    public SwarmProvider(RestClient.Builder builder) {
        this.restClient = builder.build();
    }
    private final RestClient restClient;

    private boolean waitForReadiness(String functionName, int timeoutSeconds) {
        String url = "http://fn-" + functionName + ":8080/health";
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            try {
                // Interroghiamo l'endpoint di stato /health
                var response = restClient.get()
                        .uri(url)
                        .retrieve()
                        .toBodilessEntity();

                if (response.getStatusCode().is2xxSuccessful()) {
                    return true;
                }
            } catch (Exception e) {
                // Il container non è ancora pronto (connessione rifiutata o timeout)
                // Ignoriamo l'errore momentaneo e riproviamo tra poco
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    public boolean deploy(FunctionSpec function) {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "service", "create",
                "--name", "fn-" + function.getName(),
                "--network", "nanofaas-net",
                "--replicas", String.valueOf(function.getReplicas()),
                function.getImageDigest()
        );
        Process process;
        try {
            // Situazione normale, funziona tutto
            process = pb.start();
            int exitCode = process.waitFor();
            if(exitCode != 0) return false;

            // Se siamo qua allora docker ha creato il servizio, aspettiamo che parta
            // Boh usiamo questo if strano
            int timeout = function.getTimeoutSeconds() > 0 ? function.getTimeoutSeconds() : 5;
            return waitForReadiness(function.getName(), timeout);
        } catch (IOException e) {
            // Eccezione
            return false;
        } catch (InterruptedException e) {
            // Qualcosa è andato storto
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
