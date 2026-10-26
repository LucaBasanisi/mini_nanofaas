package com.example.mini_nanofaas;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// quanto ho aggiunto il bean @Entity la prima volta mi ha dato errore.
// Devo prima modificare build.gradle e poi ANCHE fare reload gradle
// Dopo circa 2 minuti il progetto si aggiorna e non mi da più errore
// questo perché Entity non è un bean, ma un modello di rappresetnazione dei dati

@Entity
public class FunctionSpec {
    // FunctionSpec è la funzione
    // contiene immagini docker, copie che devono girare, tempo di esecuzione massimo...
    // A cosa serve? Spring riesce a capire che dovrà convertire il json quando gli arriverà un messaggio

    @Id
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
