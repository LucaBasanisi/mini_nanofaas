package com.example.mini_nanofaas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

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

    // Su H2 il sorgente è un CLOB. Il valore 10000 descrive la colonna per JPA;
    // né il setter né hasValidConfiguration() controllano la lunghezza del JSON ricevuto.
    // Se non blank, questo campo attiva l'esecuzione inline in FunctionController.invoke() al posto di Docker.
    @Lob
    @Column(length = 10000)
    private String sourceCode;

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
        // Deleghiamo la modifica al metodo di business che valida le invarianti di dominio
        updateReplicaCount(replicas);
    }
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Il timeout di esecuzione deve essere strettamente maggiore di 0 secondi.");
        }
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    // A differenza di setReplicas/setTimeoutSeconds, nessuna validazione in deserializzazione Jackson:
    // errori di sintassi o assenza di handle(String) emergono solo in deploy() o all'invocazione.
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    // strip() ignora anche whitespace Unicode: sorgenti vuoti/bianchi non deviano il routing su InlineJavaExecutor.
    public boolean hasSourceCode() {
        return this.sourceCode != null && !this.sourceCode.strip().isEmpty();
    }

    // =========================================================================================
    // METODI DI DOMINIO E LOGICA DI BUSINESS (Refactoring: Eliminazione del Code Smell "Data Class")
    // Tecnica applicata: "Move Method" e "Encapsulate Invariants" (Martin Fowler / GRASP Information Expert)
    // Trasformiamo la classe da Anemic Domain Model a Rich Domain Model dotandola di responsabilità proprie.
    // =========================================================================================

    /**
     * Aggiorna in modo sicuro il numero di repliche desiderate per la funzione,
     * proteggendo l'invariante di business (non sono ammesse repliche negative).
     *
     * @param newNumberOfReplicas Il nuovo numero di container replicati desiderato (>= 0)
     * @throws IllegalArgumentException se il numero di repliche fornito è negativo
     */
    public void updateReplicaCount(int newNumberOfReplicas) {
        if (newNumberOfReplicas < 0) {
            throw new IllegalArgumentException("Il numero di repliche desiderato non può essere negativo (valore fornito: " + newNumberOfReplicas + ")");
        }
        this.replicas = newNumberOfReplicas;
    }

    /**
     * Verifica la coerenza e la validità dell'intera specifica della funzione.
     * Serve a garantire che il sistema non accetti configurazioni corrotte, incomplete o con valori non ammessi.
     *
     * Regole di validazione:
     * - Il nome della funzione non deve essere nullo né composto solo da spazi vuoti.
     * - Il digest/tag dell'immagine Docker non deve essere nullo né vuoto.
     * - Il numero di repliche deve essere maggiore o uguale a 0 (0 indica funzione scalata a riposo).
     * - Il timeout deve essere maggiore di 0 secondi.
     *
     * @return true se tutti i vincoli di consistenza sono rispettati, false altrimenti.
     */
    public boolean hasValidConfiguration() {
        boolean isFunctionNameValid = this.name != null && !this.name.strip().isEmpty();
        // OR inclusivo (non XOR): ammette imageDigest e sourceCode insieme (es. "inline-java" dalla GUI);
        // se entrambi sono valorizzati, invoke() dà comunque priorità a hasSourceCode().
        // Nota Jackson: valori invalidi presenti nel JSON falliscono prima nei setter (400), mentre
        // campi primitivi omessi restano a 0 (replicas=0 è valido; timeoutSeconds=0 viene respinto qui sotto).
        boolean isImageDigestValid = (this.imageDigest != null && !this.imageDigest.strip().isEmpty()) || hasSourceCode();
        boolean areReplicasValid = this.replicas >= 0;
        boolean isTimeoutSecondsValid = this.timeoutSeconds > 0;

        return isFunctionNameValid && isImageDigestValid && areReplicasValid && isTimeoutSecondsValid;
    }

    // se domani decidessimo di cambiare il prefisso da fn- a nanofaas- oppure
    // la porta interna da 8080 a 80, modifichiamo solo queste righe
    public static final String SERVICE_PREFIX = "fn-";
    public static final int DEFAULT_PORT = 8080;


    // Metodi per evitare il code smell Shotgun Surgery
    public static String serviceNameFor(String name) {
        return SERVICE_PREFIX + name;
    }

    public static String healthUrlFor(String name) {
        return "http://" + serviceNameFor(name) + ":" + DEFAULT_PORT + "/health";
    }

    public static String invokeUrlFor(String name) {
        return "http://" + serviceNameFor(name) + ":" + DEFAULT_PORT + "/invoke";
    }

    public String getServiceName() {
        return serviceNameFor(this.name);
    }

    public String getHealthUrl() {
        return healthUrlFor(this.name);
    }

    public String getInvokeUrl() {
        return invokeUrlFor(this.name);
    }
}
