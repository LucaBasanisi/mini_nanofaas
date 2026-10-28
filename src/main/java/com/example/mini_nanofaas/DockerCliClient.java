package com.example.mini_nanofaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

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

        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("docker service create failed with exit code: {}", exitCode);
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
