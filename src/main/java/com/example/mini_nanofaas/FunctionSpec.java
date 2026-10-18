package com.example.mini_nanofaas;

public class FunctionSpec {
    // FunctionSpec è la funzione
    // contiene immagini docker, copie che devono girare, tempo di esecuzione massimo...
    // A cosa serve? Spring riesce a capire che dovrà convertire il json quando gli arriverà un messaggio

    private String name;
    private String imageDigest;
    private int replicas;
    private int timeoutSeconds;

    // Costruttore pieno
    public FunctionSpec(String name, String imageDigest, int replicas, int timeoutSeconds) {
        this.name = name;
        this.imageDigest = imageDigest;
        this.replicas = replicas;
        this.timeoutSeconds = timeoutSeconds;
    }

    // Costruttore vuoto
    public FunctionSpec() {}

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getImageDigest() {
        return imageDigest;
    }
    public void setImageDigest(String imageDigest) {
        this.imageDigest = imageDigest;
    }
    public int getReplicas() {
        return replicas;
    }
    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    
}
