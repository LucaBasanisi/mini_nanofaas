package com.example.mini_nanofaas;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;


/**
 * Docker Swarm.
 *    - Delega la creazione del servizio Docker CLI a {@link DockerCliClient}.
 *    - Delega la verifica dello stato di avvio e salute a {@link FunctionHealthChecker}.
 * - Non ci sono chiamate a ProcessBuilder di basso livello in questa classe.
 * - Non ci sono loop bloccanti 'while' o 'Thread.sleep()' per il controllo di readiness.
 * - È facilmente testabile con test JUnit isolando le dipendenze tramite mock.
 */

@Service
// Anche sulla rete overlay, il servizio control-plane deve ricevere NANOFAAS_PROVIDER=swarm.
// Se manca, Spring seleziona LocalProvider e un deploy di sola immagine può restituire 201
// senza che compaia alcun servizio in «docker service ls».
@ConditionalOnProperty(name = "nanofaas.provider", havingValue = "swarm")
public class SwarmProvider implements ProviderFunzione {

    private static final Logger log = LoggerFactory.getLogger(SwarmProvider.class);

    // Nome della rete overlay Docker Swarm condivisa su cui comunicano i container
    private static final String DEFAULT_NETWORK = "nanofaas-net";

    private final DockerCliClient dockerCliClient;
    private final FunctionHealthChecker healthChecker;

    public SwarmProvider(DockerCliClient dockerCliClient, FunctionHealthChecker healthChecker) {
        this.dockerCliClient = dockerCliClient;
        this.healthChecker = healthChecker;
    }

    /**
     * Esegue il deploy di una funzione su Docker Swarm:
     * Passo 1: Crea il servizio Docker Swarm tramite la CLI.
     * Passo 2: Se la creazione ha avuto successo, attende che il servizio diventi 'ready'
     *          interrogando l'endpoint di healthcheck delegato a Spring Retry.
     *
     * @param function Specifica della funzione da deployare (nome, immagine, repliche, ecc.)
     * @return true se il servizio è stato creato con successo ed è pronto a ricevere traffico, false altrimenti.
     */
    @Override
    public boolean deploy(FunctionSpec function) {
        String serviceName = getServiceName(function.getName());
        log.info("Avvio del deploy per la funzione '{}' (immagine: {}, repliche: {})",
                function.getName(), function.getImageDigest(), function.getReplicas());

        // PASSO 1: Esecuzione del comando Docker Swarm CLI ('docker service create ...')
        boolean created = dockerCliClient.createService(
                serviceName,
                DEFAULT_NETWORK,
                function.getReplicas(),
                function.getImageDigest()
        );

        if (!created) {
            log.error("Creazione del servizio Docker Swarm fallita per la funzione '{}'", function.getName());
            return false;
        }

        // PASSO 2: Verifica di readiness del servizio appena avviato
        // La logica di polling e retry con backoff è completamente gestita da FunctionHealthChecker tramite Spring Retry.
        return healthChecker.checkReadiness(function.getName());
    }

    @Override
    public boolean remove(String functionName) {
        String serviceName = getServiceName(functionName);
        return dockerCliClient.removeService(serviceName);
    }

    @Override
    public boolean scale(String functionName, int replicas) {
        String serviceName = getServiceName(functionName);
        return dockerCliClient.scaleService(serviceName, replicas);
    }

    private static @NonNull String getServiceName(String functionName) {
        return "fn-" + functionName;
    }

}
