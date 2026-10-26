package com.example.mini_nanofaas;

import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SwarmProvider {

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
            process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
