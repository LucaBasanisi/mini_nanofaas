package com.example.mini_nanofaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Servizio dedicato a comporre e inviare i comandi docker. Serve a scollegare la logica della cli di docker dallo {@link SwarmProvider}
 * Utilizza Process Builder per
 */


@Service
public class DockerCliClient {



    private static final Logger log = LoggerFactory.getLogger(DockerCliClient.class);

    public boolean createService(String serviceName, String network, int replicas, String imageDigest) {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "service", "create",
                "--name", serviceName,
                "--network", network,
                "--replicas", String.valueOf(replicas),
                imageDigest
        );

        return executeCommand(pb);
    }


    public boolean removeService(String serviceName) {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "service", "remove",
                serviceName
        );
        return executeCommand(pb);
    }

    public boolean scaleService(String serviceName, int replicas) {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "service", "update",
                "--replicas", String.valueOf(replicas),
                serviceName
        );

        // The command will return immediately, but the actual scaling of the service may take some time.
        // To stop all replicas of a service while keeping the service active in the swarm you can set the scale to 0.

        return executeCommand(pb);
    }



    private static boolean executeCommand(ProcessBuilder pb) {
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("docker service failed with exit code: {}", exitCode);
                return false;
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to execute docker command", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Execution interrupted while waiting for docker command", e);
            return false;
        }
    }



}
