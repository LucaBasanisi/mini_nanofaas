This file is a merged representation of a subset of the codebase, containing specifically included files and files not matching ignore patterns, combined into a single document by Repomix.
The content has been processed where empty lines have been removed.

# File Summary

## Purpose
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.

## File Format
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  a. A header with the file path (## File: path/to/file)
  b. The full contents of the file in a code block

## Usage Guidelines
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.

## Notes
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: src/main/**/*.java, src/main/resources/**/*.properties, src/main/resources/**/*.yml, src/main/resources/**/*.yaml, build.gradle, Dockerfile, README.md, ROADMAP.md, OttimizzaFunzioni.md
- Files matching these patterns are excluded: **/build/**, **/.gradle/**, **/gradlew*, **/*.iml, **/.idea/**, docs/**
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Empty lines have been removed from all files
- Files are sorted by Git change count (files with more changes are at the bottom)

# Directory Structure
````
src/
  main/
    java/
      com/
        example/
          mini_nanofaas/
            ContainerNotReadyException.java
            DockerCliClient.java
            FunctionController.java
            FunctionDispatcher.java
            FunctionHealthChecker.java
            FunctionRegistry.java
            FunctionRepository.java
            FunctionSpec.java
            MiniNanofaasApplication.java
            ProviderFunzione.java
            SwarmProvider.java
    resources/
      application.properties
build.gradle
Dockerfile
OttimizzaFunzioni.md
README.md
ROADMAP.md
````

# Files

## File: src/main/java/com/example/mini_nanofaas/ContainerNotReadyException.java
````java
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
````

## File: src/main/java/com/example/mini_nanofaas/ProviderFunzione.java
````java
package com.example.mini_nanofaas;
public interface ProviderFunzione {
    boolean deploy(FunctionSpec function);
    boolean remove(String functionName);
    boolean scale(String functionName, int replicas);
}
````

## File: Dockerfile
````dockerfile
# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copia i file di configurazione Gradle e il wrapper per sfruttare il caching dei layer Docker
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

# Scarica le dipendenze in anticipo
RUN ./gradlew dependencies --no-daemon || true

# Copia i sorgenti ed esegui la build saltando i test (già verificati in CI)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime minimale
FROM eclipse-temurin:25-jre
WORKDIR /app

# Installa la CLI di Docker per consentire a SwarmProvider di eseguire comandi sullo Swarm
RUN apt-get update && \
    apt-get install -y --no-install-recommends docker.io curl && \
    rm -rf /var/lib/apt/lists/*

# Copia il JAR compilato dallo stage precedente
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
````

## File: OttimizzaFunzioni.md
````markdown
# **Ottimizzazione Funzioni nanofaas**


*in realtà UPM lo eliminato, dovrei riscrivere il kernel linux e mi sembra un pò troppo complicato*

Esempio funzione Python (per java oppure rust ovviamente sarà diverso):

Watchdog / Control Plane
        │  
        ▼  
Avvio Python Seed Process (carica solo PSL: Flask, runtime base)  
        │  
        ▼  
In attesa di richieste...  
  ┌─────┴────────────────────────────┐  
  Arriva un Burst (N richieste)      Torna in Idle (nessuna richiesta)  
  │                                  │ 
  ▼                                  ▼ 
\[ERUN \- Load\]                      \[ERUN \- Shrink\]  
Carica le FSL (es. numpy)          Scarica le FSL dalla RAM del processo,  
dal singleLibStore sul nodo        torna leggero in attesa del prossimo burst  
  │  
  ▼  
\[MXFaaS \- Fork\]  
Esegue N fork() in Copy-On-Write  
per servire le richieste in parallelo  
condividendo PSL \+ FSL appena caricate\!

# **Piano D’azione**

Costruire un simulatore usando solamente docker swarm (per semplicità) per vedere se questa soluzione è fattibile.  
ERUN  →  contributo principale (implementativo)  
MXFaaS      →  contributo secondario per Python/Go.   
CRaC        →  contributo per Java al posto di MXFaaS

# **Spiegazione delle Tecnologie**

## ERUN

In una situazione di cold start viene creato lo snapshot della funzione. Se siamo in una situazione di warm start, il pod usa ERUN, ovvero Ricarica FSL da singleLibStore.   
Considerazioni: Lo store in-memory condiviso sul nodo previsto da ERUN non solo è realizzabile ma è relativamente semplice se usi container (Docker / Kubernetes). Lo store condiviso a livello di nodo (singleLibStore) non deve far parte del codice Java del Control Plane, ma è un componente infrastrutturale del nodo worker:

* Si crea sul nodo host una directory montata in RAM tramite tmpfs (o usando /dev/shm).  
* Si monta questa directory in modalità read-only (o read-write controllato da un agente locale) come volume condiviso (hostPath in Kubernetes) all'interno di tutti i pod delle funzioni che girano su quello specifico nodo.  
* Quando una funzione in Python esegue il caricamento dinamico (o il reload da snapshot delle FSL), legge direttamente dalle pagine in RAM del tmpfs locale al nodo tramite mmap, ottenendo latenze prossime allo zero senza passare dal disco.

Quando la funzione finisce → Usiamo lo SHRINK di ERUN → Unload FSL, il nodo worker torna in idle. 

## MXFaaS

Implemento MXFaaS, che usa la Copy On Write per creare tante fork della funzione facendogli puntare allo stesso indirizzo di memoria per risparmiare. L'MXContainer di MXFaaS inizializza prima lo stato del container, e solo successivamente, *on demand*, crea un processo per ogni invocazione — in modo che tutte le invocazioni possano condividere lo **stato di memoria non modificato**, minimizzando l'impronta in RAM. 

## Problemi per MXFaaS

La tua soluzione ha un'idea interessante: mantenere un processo Python leggero, caricare le librerie solo quando servono e sfruttare `fork()` per condividere la memoria tra più worker. Il problema è che stai combinando tre meccanismi delicati: gestione dinamica delle librerie, Copy-On-Write e gestione del ciclo di vita dei processi.

Vediamo gli scenari limite in cui l'architettura può rompersi davvero, non semplicemente diventare un po' meno efficiente.

## **1\. Una libreria viene scaricata mentre un worker la sta usando**

Scenario

Il seed ha caricato `numpy`, poi ha creato un worker che sta eseguendo un calcolo.

Worker 1

Sta usando un array NumPy e le funzioni della libreria.

Seed

Riceve il segnale di idle e tenta di scaricare NumPy.

Il worker potrebbe essere ancora attivo quando il seed avvia lo shrink.

Che cosa succede?

Se il seed elimina semplicemente i riferimenti ai moduli, il worker figlio non perde automaticamente i propri riferimenti: dopo `fork()`, i due processi hanno spazi di indirizzamento distinti. Quindi il problema non è necessariamente un crash immediato.

Il problema reale è che:

* il seed potrebbe non liberare la memoria quanto previsto;  
* un worker ancora attivo continua a consumare RAM;  
* se lo shrink comporta la terminazione forzata di processi o altre operazioni non sicure, potresti interrompere richieste in corso;  
* un nuovo burst potrebbe arrivare mentre il vecchio gruppo di worker non è ancora terminato.

Come evitarlo: lo shrink deve avvenire soltanto dopo aver drenato le richieste, verificato che i worker siano terminati e predisposto una transizione di stato sicura.

## **3\. Una libreria nativa lascia un lock bloccato dopo `fork()`**

Possibile deadlock

Questo è uno dei casi più insidiosi in Python, soprattutto con librerie scientifiche.

Immagina che il seed abbia caricato NumPy, che a sua volta utilizzi codice nativo o librerie matematiche multithread come OpenBLAS.

1. Un thread del seed acquisisce un lock interno della libreria.  
2. Il seed esegue `fork()`.  
3. Il worker figlio eredita la memoria, compreso lo stato del lock.  
4. Il thread che possedeva quel lock non esiste nel figlio, perché `fork()` replica soltanto il thread chiamante.

Il worker prova a usare la libreria e rimane bloccato aspettando un lock che nessuno potrà più rilasciare.

Questo scenario non si verifica con ogni libreria né a ogni fork, ma è un rischio concreto quando si combina `fork()` con thread e librerie native.

Conseguenza: una funzione apparentemente innocua può smettere di rispondere senza produrre alcun errore evidente.

Possibile rimedio: controllare la compatibilità delle librerie con `fork()`, evitare fork da processi multithread in stati non sicuri e valutare un diverso metodo di avvio dei worker. Quest'ultima scelta, però, potrebbe compromettere la condivisione della memoria che vuoi ottenere.

## **4\. Arriva un burst enorme mentre stai caricando le librerie**

Concorrenza

Supponiamo che 100 richieste arrivino quasi contemporaneamente e che tutte richiedano la stessa libreria.

Se il sistema non coordina correttamente le richieste, potresti ritrovarti con questa situazione:

* il watchdog rileva il burst;  
* il seed comincia a caricare una libreria da disco;  
* un altro componente interpreta lo stato come non ancora pronto e avvia un secondo caricamento;  
* altre richieste vengono messe in coda oppure tentano di creare worker prima che il caricamento sia terminato.

Se il caricamento avviene correttamente una sola volta, il problema non si presenta. Ma senza sincronizzazione, puoi avere duplicazioni, errori di inizializzazione o richieste gestite in uno stato incoerente.

Possibile rimedio: una macchina a stati esplicita, ad esempio `IDLE → LOADING → READY → DRAINING → SHRINKING`, con un'unica operazione di caricamento per ciascuna generazione di worker.

## **5\. Il seed viene aggiornato dopo il fork, ma i worker non vedono l'aggiornamento**

Coerenza dei dati

Questo caso riguarda soprattutto le librerie o i dati che cambiano durante l'esecuzione.

1. Il seed carica la versione A di una configurazione o di un modello.  
2. Crea otto worker con `fork()`.  
3. Il seed aggiorna la configurazione alla versione B.  
4. I worker continuano a vedere la propria copia logica dello stato precedente.

Il Copy-On-Write non è un sistema di sincronizzazione: le modifiche effettuate nel processo padre dopo il fork non vengono propagate automaticamente ai figli.

Se il tuo sistema aggiorna librerie o modelli mentre i worker sono attivi, potresti quindi avere richieste che usano versioni differenti.

Possibile rimedio: associare ogni gruppo di worker a una versione immutabile delle librerie e dei dati, e creare una nuova generazione quando serve aggiornare qualcosa.

## **6\. Il worker termina, ma il sistema non recupera le risorse**

Esaurimento delle risorse

Immagina un burst continuo, con centinaia di richieste al secondo. Il seed crea un processo per richiesta, ma alcune richieste falliscono e il codice di gestione dei processi non raccoglie correttamente i processi terminati.

Su sistemi Unix, i figli terminati che non vengono raccolti possono diventare processi zombie. Se invece i processi rimangono attivi bloccati su operazioni I/O, consumano risorse reali.

Conseguenze possibili:

* esaurimento del limite di processi;  
* aumento del numero di descrittori di file;  
* crescita della memoria complessiva;  
* aumento della latenza fino al mancato servizio delle richieste.

Possibile rimedio: limite massimo di worker, gestione corretta di `wait()`/`waitpid()`, timeout, terminazione controllata e backpressure. Creare un processo per ogni richiesta non è necessariamente sostenibile, anche quando il fork è veloce.

## **7\. Il processo seed muore e il Control Plane non sa che cosa è successo**

Resilienza

Immagina che il seed carichi correttamente le librerie e generi dieci worker. Poi viene terminato per un errore, un crash nativo o un riavvio del nodo.

Se il Control Plane considera il seed come l'unica fonte di verità, potrebbe perdere lo stato dei worker o non sapere quali richieste siano ancora in esecuzione.

La situazione può essere ancora peggiore se i worker sopravvivono al seed: il sistema potrebbe avviare un nuovo seed e generare altri worker, mentre quelli vecchi continuano a lavorare.

Possibile rimedio: identificativi di generazione, heartbeat, supervisione dei processi, timeout delle richieste e una procedura di recupero che riconcili lo stato desiderato con quello effettivo.

## **8\. Il vero caso limite: il costo dello shrink supera il beneficio**

Prestazioni

Immagina un carico che alterna un burst ogni due secondi a un secondo di inattività.

La tua architettura potrebbe eseguire continuamente:

1. caricamento delle librerie;  
2. fork dei worker;  
3. elaborazione delle richieste;  
4. terminazione dei worker;  
5. tentativo di scaricamento delle librerie;  
6. nuovo caricamento al burst successivo.

Se il caricamento richiede 800 ms e lo shrink e la ricreazione dei worker introducono altri costi significativi, il sistema spreca una parte importante del tempo a prepararsi per lavorare anziché a lavorare.

Inoltre, eliminare i riferimenti a un modulo Python non garantisce che tutta la RAM venga restituita al sistema operativo. Gli oggetti ancora referenziati, gli allocator e le librerie native possono mantenere memoria allocata.

Possibile rimedio: isteresi e soglie temporali, ad esempio mantenere le librerie in memoria per un periodo minimo dopo l'ultima richiesta, anziché scaricarle appena il carico torna a zero. **questa è una soluzione estremamente buona.**

## **Quali sono i problemi più seri per il tuo progetto?**

| Problema | Gravità potenziale | Priorità |
| ----- | ----- | ----- |
| Lock nativi ereditati dopo `fork()` | Worker bloccati | Alta |
| Copy-On-Write compromesso | Consumo eccessivo di RAM | Alta |
| Burst durante il caricamento | Race condition | Alta |
| Worker non terminati correttamente | Esaurimento risorse | Alta |
| Seed terminato inaspettatamente | Stato incoerente | Alta |
| Shrink troppo frequente | Prestazioni peggiori | Media |
| Aggiornamento dei dati dopo il fork | Risultati incoerenti | Media |

La mia valutazione è che il punto più difficile non sia implementare `fork()`, ma garantire che caricamento, fork, gestione delle richieste e shrink costituiscano un ciclo di vita coerente.

La soluzione NON è costruire una rete di petri\!

Comunque sarebbe meglio creare un diagramma degli stati per visualizzare al meglio il tutto.

### Forkare processo java

Il problema da capire è che forkare un processo Java è poco fattibile.

Thread JVM interni che muoiono nel fork():  
├── GC threads (G1, ZGC, ecc.)  
├── JIT compiler thread (C1, C2)  
├── Signal dispatcher thread  
├── Finalizer thread  
├── Common ForkJoinPool workers  
└── ... altri thread interni

Mutex che restano bloccati nel figlio:  
├── Safepoint mechanism locks  
├── ClassLoader locks  
├── Heap locks  
└── Internal monitor locks

Il risultato tipico è un **deadlock silenzioso** al primo accesso a qualsiasi struttura dati JVM nel processo figlio.

### ERUN con Classloader \+ CRaC

La soluzione è **CRaC (Coordinated Restore at Checkpoint)** è un progetto OpenJDK che risolve esattamente questo problema in modo coordinato. È disponibile su Azul Zulu, Amazon Corretto, e **Spring Boot 3.2+ lo supporta nativamente**.

L’idea è che invece di un fork() brutale, l’applicazione viene **avvisata** prima del checkpoint e può fare cleanup ordinato:

Container Java avviato  
        │  
        ▼  
Spring Boot si inizializza completamente  
(carica classi, PSL, tutto)  
        │  
        ▼  
\[CHECKPOINT CRaC\]  
JVM avvisa l'app → beforeCheckpoint()  
CRIU prende lo snapshot del processo  
        │  
        ├──→ Restore 1 → afterRestore() → esegue funzione A  
        ├──→ Restore 2 → afterRestore() → esegue funzione B  
        └──→ Restore 3 → afterRestore() → esegue funzione C

Ogni restore condivide le pagine read-only (codice, classi caricate) esattamente come il fork COW di MXFaaS. **AWS Lambda SnapStart usa esattamente questo meccanismo**.

![][image2]

Attenzione\!

1. piattaforma scritta in Java Spring Boot e compilata con GraalVM (quindi in un eseguibile nativo AOT tramite *Native Image*). Questo significa che i moduli che gestiscono lo scheduling, il routing e il controllo consumano pochissima RAM e si avviano istantaneamente.  
2. Le funzioni scritte dagli utenti girano nei propri ambienti di esecuzione (es. l'interprete Python con Flask/gunicorn, oppure una JVM per funzioni Java, o binari Go/Rust).

Il fatto che la piattaforma FaaS sia compilata con GraalVM **non impedisce affatto di usare ERUN o CRaC per le funzioni**, a patto che le funzioni degli utenti girino su runtime dinamici (come Python o una JVM standard). Al contrario, se anche le *funzioni Java degli utenti* venissero compilate ahead-of-time con GraalVM Native Image, allora diventeranno binari statici nativi: in quel caso perdi il ClassLoader dinamico della JVM (quindi niente ERUN tramite Garbage Collector né CRaC), ma guadagni un binario nativo che si avvia in pochi millisecondi e che **può** usare direttamente la `fork()` di MXFaaS esattamente come faresti per C, Go o Rust, perché non ha i thread del JIT compiler o la complessità di inizializzazione di una JVM tradizionale\!

### Problema di CRaC

Il meccanismo di checkpoint/restore di CRaC è compatibile con i Virtual Threads di Java? Sì, ma c’è un però.

Se usi i Virtual Threads dentro un container Java, ti serve davvero fare più restore paralleli nello stesso nodo per gestire la concorrenza come in MXFaaS? 

Con i Virtual Threads, un singolo processo JVM può gestire migliaia di invocazioni concorrenti bloccanti (I/O) con un'impronta di memoria irrisoria (pochi kilobyte di stack per invocazione), condividendo già nativamente tutto il codice e le classi caricate (PSL e FSL) senza bisogno di fork() o di CRIU per la concorrenza intra-container. CRaC rimarrebbe quindi utile quasi esclusivamente per abbattere il cold start della prima istanza, mentre la concorrenza burst verrebbe assorbita dai Virtual Threads.

Non a caso l’altra soluzione (più brutta) è semplicemente non implementare CRaC per Java. Gemini 4 consiglia proprio questa soluzione.

## Compilatori statici

\[MXFaaS\] fork dal binario inizializzato  
\[UPM\] mark segmento .text (codice)  
← KSM fonde codice identico tra container

Nella sezione sui compilatori statici proponi di usare MXFaaS per fare il fork dal binario inizializzato e UPM per marcare il segmento `.text` (codice) in modo che KSM fonda il codice identico tra container. 

**Attenzione a come funziona il kernel Linux:** per il segmento `.text` (che è codice eseguibile *read-only* mappato da file), **non serve KSM/UPM\!** Se più container sullo stesso nodo eseguono lo stesso binario (perché condividono il layer dell'immagine container tramite OverlayFS o caricano il binario dallo stesso volume sul nodo), il kernel Linux mappa il segmento `.text` in memoria tramite la normale *Page Cache* condivisa: le pagine fisiche in RAM sono **già** condivise nativamente dal kernel tra tutti i processi. 

KSM (`MADV_MERGEABLE`) funziona solo sulle **pagine anonime** (come l'heap allocato dinamicamente con `malloc`/`mmap(MAP_ANONYMOUS)` o le strutture dati create a runtime dall'interprete Python). Nei linguaggi compilati staticamente, dunque, UPM ha utilità solo se hai grandi strutture dati statiche inizializzate nell'heap durante la fase di init pre-richiesta.

Insomma anche qua ci sono grossi problemi, potrei semplificarmi la vita e non implementare questa soluzione per 

# 

[image1]: <data:image/png;base64,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>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAABPoAAAKGCAYAAADTSvUsAACAAElEQVR4Xuzd939UVeI38OenZ8t363d3FVyxsGtZddV1bSAoRXrvvXdCJxB66BBCL4FQQg0kgYQASSD0IiAICKKIIlWUous+/8F58jnx3DlzzrSESTIz9/PD+wW555zbZu6E+XDK//nLX/4iiIiIiIiIiIiIKLr9H3MDERERERERERERRR8GfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREREREREQUAxj0ERERERERERERxQAGfURERERERERERDGAQR8REREREREREVEMYNBHREREFILXnntcNHnnCdGr3pMioU0VMb3zU0QUIfBM4tnEM4pn1Xx+iYiI3IJBHxEREZEfNV+rJBI7PiVOzPibuJXynDg94xlROL6K2DG8ksiMe4yIIsT2YZVE4bgnxemZz8hn9aMZVeWzi2fYfK6JiIhiGYM+IiIiIgN6BK0c+LS4k/KcyB5eWcxp8zvR593/S0RRAs9szojK8hleVfQss5cfERG5BYM+IiIiIg2GAP60/gWROeRxMajGL60AgYiiB57hrKGV5DM9rm0V63knIiKKNQz6iIiIiH62bvBT4sysZ8WY+r+2AgMiil54pvFs4xk3n3siIqJYwqCPiIiIqEje+GdFwdgnrYCAiGIH5vErmPCs9fwTERHFCgZ9RERE5Hro5ZOfUMUKBYgo9uBZZ88+IiKKVQz6iIiIyNUmtasih/SZYQARxS4883j2zc8DIiKiaMegj4iI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>
````

## File: src/main/java/com/example/mini_nanofaas/FunctionHealthChecker.java
````java
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
````

## File: src/main/java/com/example/mini_nanofaas/FunctionRepository.java
````java
package com.example.mini_nanofaas;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FunctionRepository extends JpaRepository<FunctionSpec, String> {
    // T è il TIPO dell'oggetto nel database, nel nostro caso sarà FunctionSpec
    // ID è la Primary Key
    // A runtime viene generata una classe proxy che implementa ricerca, salvataggio, etc... per il database
}
````

## File: src/main/resources/application.properties
````
spring.application.name=mini_nanofaas
management.endpoints.web.exposure.include=health, prometheus
````

## File: src/main/java/com/example/mini_nanofaas/DockerCliClient.java
````java
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
````

## File: src/main/java/com/example/mini_nanofaas/FunctionDispatcher.java
````java
package com.example.mini_nanofaas;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component
public class FunctionDispatcher {
    /*
    Il FunctionController avvia il metodo "invoke" e lo invia al FunctionDispatcher
    - costruiamo l'indirizzo di rete attorno al container
    - facciamo una chiamata HTTP al container usando RestClient
    - inviamo la risposta al controller
     */
    /*
    Scriviamo il costruttore, però non va bene scriverlo così
    public FunctionDispatcher(RestClient restClient) {
        this.restClient = restClient;
    }
    Per costruire FunctionDispatcher, ho bisogno di un oggetto di tipo RestClient. Vado a cercarne uno nel mio magazzino!"
    Spring Boot pre-configura tantissime cose automaticamente, ma non crea un
    RestClient "finito". Perché? Perché in un'applicazione reale, potresti voler chiamare più servizi diversi
    con configurazioni diverse:
  • Un servizio con timeout a 2 secondi e autenticazione OAuth.
  • Un altro servizio con timeout a 30 secondi e certificato SSL custom.
    Se Spring creasse un unico RestClient globale nel magazzino, non saprebbe con quali impostazioni lo vuoi!
    Spring Boot allora mette nel magazzino un "kit di montaggio" già parzialmente configurato:
    il RestClient.Builder.
    */
    public FunctionDispatcher(RestClient.Builder builder) {
        this.restClient = builder.build();
    }
    private final RestClient restClient;
    // Metodo dispatch
    // String url = "http://fn-" + functionName + ":8080/invoke";
    // (Ad esempio, se functionName è "saluta", allora http://fn-saluta:8080/invoke).
    public String dispatch(String functionName, String payload) {
        // creiamo la richiesta/chiamata per il container
        var request = restClient.post()
                .uri(FunctionSpec.invokeUrlFor(functionName));
        // Se esiste il payload lo aggiungiamo, altrimenti no
        if(payload != null) {
            request.body(payload);
        }
        // Eseguiamo la chiamata
        return request.retrieve()
                .body(String.class);
    }
}
````

## File: src/main/java/com/example/mini_nanofaas/FunctionSpec.java
````java
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
        boolean isImageDigestValid = this.imageDigest != null && !this.imageDigest.strip().isEmpty();
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
````

## File: build.gradle
````
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
description = 'mini_nanofaas'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}


// notare come le varie dipendenze hanno anche l'opzione per runtimeOnly oppure testRuntimeOnly
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.retry:spring-retry:2.0.11'
    implementation 'org.springframework:spring-aspects'
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
    runtimeOnly 'com.h2database:h2'
}

tasks.named('test') {
    useJUnitPlatform()
}
````

## File: src/main/java/com/example/mini_nanofaas/MiniNanofaasApplication.java
````java
package com.example.mini_nanofaas;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;
@SpringBootApplication
@EnableRetry
public class MiniNanofaasApplication {
    static void main(String[] args) {
        SpringApplication.run(MiniNanofaasApplication.class, args);
    }
    // Per creare il function dispatcher ho bisogno di creare fin da subito un RestClient builder
    // è un oggetto generico, che poi io specializzo
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
````

## File: src/main/java/com/example/mini_nanofaas/SwarmProvider.java
````java
package com.example.mini_nanofaas;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
````

## File: src/main/java/com/example/mini_nanofaas/FunctionRegistry.java
````java
package com.example.mini_nanofaas;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public class FunctionRegistry {
    private final FunctionRepository repository;
    public FunctionRegistry(FunctionRepository repository) {
        this.repository = repository;
    }
    // Salva o Aggiorna la funzione
    public boolean registerOrSaveFunction(FunctionSpec function) {
        if (repository.existsById(function.getName())) {
            return false;
        }
        repository.save(function);
        return true;
    }
    // Metodo get: cerca per nome (chiave primaria)
    public Optional<FunctionSpec> getFunction(String functionName) {
        return repository.findById(functionName);
    }
    // Metodo exists: verifica presenza per chiave primaria
    public boolean existsFunction(String functionName) {
        return repository.existsById(functionName);
    }
    public void deleteFunction(String functionName) {
        repository.deleteById(functionName);
    }
    /**
     * Aggiorna lo stato delle repliche nel database H2 dopo l'avvenuto scaling su Docker Swarm.
     * Applica la tecnica Move Method delegando a {@link FunctionSpec#updateReplicaCount(int)}
     * la mutazione protetta del valore invece di manipolare campi grezzi dall'esterno.
     *
     * @param functionName Nome identificativo univoco della funzione da aggiornare
     * @param newNumberOfReplicas Nuovo numero di container desiderato
     * @return true se la funzione è presente nel registro ed è stata aggiornata, false altrimenti
     */
    public boolean updateReplicas(String functionName, int newNumberOfReplicas) {
        Optional<FunctionSpec> optionalFunctionSpecification = repository.findById(functionName);
        if (optionalFunctionSpecification.isPresent()) {
            FunctionSpec functionSpecification = optionalFunctionSpecification.get();
            // Deleghiamo a FunctionSpec la mutazione protetta delle repliche (Rich Domain Model)
            functionSpecification.updateReplicaCount(newNumberOfReplicas);
            repository.save(functionSpecification);
            return true;
        }
        return false;
    }
}
````

## File: README.md
````markdown
# mini_nanofaas

Una piattaforma **Function-as-a-Service (FaaS)** didattica, leggera e modulare ispirata a [nanofaas](https://github.com/miciav/nanofaas).  
Il progetto è realizzato con **Java 25**, **Spring Boot 4.x** e **Docker Swarm**.
---

## 🏛️ Architettura

Il sistema si compone di 4 moduli architetturali chiave:

```mermaid
flowchart TD
    Client(["Client / API Caller"])
    
    subgraph ControlPlane ["Control Plane (Spring Boot)"]
        Controller["FunctionController (/v1/functions)"]
        Registry["FunctionRegistry (H2 / JPA)"]
        Dispatcher["FunctionDispatcher (RestClient)"]
        Actuator["Actuator / Prometheus Metrics"]
        Provider["SwarmProvider (Docker CLI)"]
    end

    subgraph SwarmNet ["Docker Swarm Overlay Network (nanofaas-net)"]
        FnA["fn-greeter (Container)"]
        FnB["fn-worker (Container)"]
        Prometheus["Prometheus Server"]
    end

    Client -->|1. Register / Invoke| Controller
    Controller -->|Read/Write Spec| Registry
    Controller -->|Deploy Service| Provider
    Provider -.->|docker service create| SwarmNet
    Controller -->|Dispatch Execution| Dispatcher
    Dispatcher -->|HTTP /invoke| FnA
    Prometheus -->|Scrape /actuator/prometheus| Actuator
```

1. **REST API & Control Plane**: Endpoint di gestione per registrare specifiche di funzioni (`POST /v1/functions`) e invocarle sincronicamente (`POST /v1/functions/{name}:invoke`).
2. **Function Registry**: Catalogo delle funzioni registrate (`FunctionSpec`), con persistenza tramite Spring Data JPA e database H2.
3. **Swarm Provider & DockerCliClient**: Gestore del ciclo di vita dei container su cluster Docker Swarm (`create`, `scale`, `remove`, health-check). La logica di esecuzione dei comandi di sistema verso la Docker CLI è isolata in `DockerCliClient`, consentendo di testare `SwarmProvider` in un test JUnit mockando `DockerCliClient` senza dover avere Docker installato e attivo nella macchina di test.
4. **Dispatcher**: Routing e inoltro HTTP delle richieste direttamente ai container/servizi dedicati sulla rete overlay (`http://fn-<name>:8080/invoke`).

---

## 🚀 Stack Tecnologico

- **Linguaggio**: Java 25
- **Framework**: Spring Boot 4.1.x
- **Build Tool**: Gradle (Wrapper)
- **Container Orchestration**: Docker Swarm con overlay network (`nanofaas-net`)
- **Ambiente Isolato & Provisioning**: Multipass (Ubuntu VM) + script Python
- **Osservabilità**: Spring Boot Actuator, Micrometer, Prometheus

---

## 📋 Specifiche di una Funzione (`FunctionSpec`)

Ogni funzione registrata è descritta da un payload JSON:

```json
{
  "name": "greeter",
  "imageDigest": "repo/greeter:latest",
  "replicas": 1,
  "timeoutSeconds": 30
}
```

---

## 🛠️ API Endpoints

| Metodo | Endpoint | Descrizione | Status Code |
| :--- | :--- | :--- | :--- |
| `POST` | `/v1/functions` | Registra e deploya una nuova funzione su Swarm | `201 Created` / `409 Conflict` / `500 Internal Server Error` |
| `POST` | `/v1/functions/{name}:invoke` | Inoltra ed esegue una chiamata alla funzione | `200 OK` / `404 Not Found` |
| `POST` | `/v1/functions/{name}/scale` | Ridimensiona il numero di repliche su Swarm e aggiorna H2 (`?replicas=N`) | `200 OK` / `404 Not Found` / `500 Internal Server Error` |
| `DELETE` | `/v1/functions/{name}` | Elimina il servizio Swarm e rimuove la funzione da H2 | `200 OK` / `404 Not Found` / `500 Internal Server Error` |
| `GET` | `/actuator/prometheus` | Metriche per il monitoring con Prometheus | `200 OK` |
| `GET` | `/actuator/health` | Health check del Control Plane | `200 OK` |

---

## 🧭 Roadmap di Sviluppo

Lo sviluppo è tracciato dettagliatamente all'interno di [ROADMAP.md](ROADMAP.md):

- [x] **Fase 1**: Core Control Plane (API REST, Dispatcher, Registry H2/JPA, Metriche Prometheus, Pipeline CI).
- [x] **Fase 2**: Provisioning VM con Multipass & `cloud-init`, Dockerfile multi-stage, Pipeline CD su GHCR e deploy su Docker Swarm.
- [ ] **Fase 3 (In corso)**: Implementazione dello Swarm Provider (deploy container con `ProcessBuilder`, readiness check, scaling e rimozione).
- [ ] **Fase 4**: Deploy server Prometheus, container funzione demo e CLI Python per sviluppatori.
- [ ] **Fase Futura**: Stateful Serverless via Watchdog sidecar in Rust con storage di stato distribuito su Redis.

---

## 🚀 Guida all'Installazione e Avvio Rapido

Il sistema non è pensato per girare come applicativo standalone sull'host, ma all'interno di un ambiente isolato con **Docker Swarm** e rete overlay dedicato, orchestrato tramite **Multipass**.

### 1. Prerequisiti
- **Git** installato sulla macchina host.
- **Multipass** installato sull'host (`snap install multipass` o gestore pacchetti di sistema).

*(Nota per utenti Fedora/RHEL)*: se `firewalld` è attivo, concedere fiducia ai bridge Multipass per consentire il rilascio dell'IP via DHCP:
```bash
sudo firewall-cmd --permanent --zone=trusted --add-interface=mpqemubrzone1
sudo firewall-cmd --permanent --zone=trusted --add-interface=mpqemubrzone2
sudo firewall-cmd --permanent --zone=trusted --add-interface=mpqemubrzone3
sudo firewall-cmd --reload
```

---

### 2. Clonare il Repository
```bash
git clone https://github.com/LucaBasanisi/mini_nanofaas.git
cd mini_nanofaas
```

---

### 3. Provisioning della VM con Multipass (`cloud-init`)
Lancia la VM Ubuntu 24.04 indicando il file di bootstrap [infra/cloud-init.yaml](infra/cloud-init.yaml) (che installerà Docker, inizializzerà Docker Swarm e creerà la rete overlay `nanofaas-net`):

```bash
multipass launch 24.04 --name nanofaas-vm --cpus 2 --memory 2G --disk 10G --cloud-init infra/cloud-init.yaml
```

Verifica che la VM sia nello stato `Running` e abbia un IP IPv4 assegnato:
```bash
multipass list
```

---

### 4. Accesso alla Shell della VM
Entra direttamente nella shell interattiva della VM:
```bash
multipass shell nanofaas-vm
```

*(Opzionale)* Verifica che Swarm e la rete overlay siano attivi:
```bash
docker info
docker network ls
```

---

### 5. Avvio del Control Plane (Docker Swarm Service)
All'interno della shell della VM, avvia il servizio del Control Plane. L'immagine OCI multi-stage viene scaricata direttamente da **GitHub Container Registry (GHCR)**:

```bash
docker service create \
  --name control-plane \
  --network nanofaas-net \
  --publish published=8080,target=8080 \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
  ghcr.io/lucabasanisi/mini_nanofaas-controlplane:latest
```

Verifica che il servizio abbia raggiunto lo stato desiderato (`1/1` repliche) e consulta i log di Spring Boot:
```bash
docker service ls
docker service logs -f control-plane
```

---

### 6. Test e Utilizzo delle API
Puoi testare gli endpoint sia dall'interno della VM (`localhost:8080`), sia dall'host usando l'IP assegnato da Multipass alla VM (es. `10.89.24.201:8080`):

#### Health Check
```bash
curl http://localhost:8080/actuator/health
```

#### Registrazione e Deploy di una Funzione
```bash
curl -i -X POST http://localhost:8080/v1/functions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "greeter",
    "imageDigest": "alpine:latest",
    "replicas": 1,
    "timeoutSeconds": 30
  }'
```

#### Invocazione di una Funzione
```bash
curl -X POST http://localhost:8080/v1/functions/greeter:invoke \
  -H "Content-Type: text/plain" \
  -d "Ciao Mondo"
```
````

## File: src/main/java/com/example/mini_nanofaas/FunctionController.java
````java
package com.example.mini_nanofaas;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/v1/functions")
public class FunctionController {
    /*
     @RestController indica a Spring che questa classe riceve richieste HTTP e restituisce dati JSON
     @RequestMapping definisce il prefisso dell'URL per tutti i metodi di questa classe
    */
    // Ci servirà il  dispatcher per effettuare la chiamata al container
    // Questo è un Constructor Injection
    private final FunctionDispatcher dispatcher;
    private final FunctionRegistry registry;
    private final ProviderFunzione functionProvider;
    public FunctionController(FunctionDispatcher dispatcher, FunctionRegistry registry,  ProviderFunzione provider) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.functionProvider = provider;
    }
    @PostMapping
    public ResponseEntity<FunctionSpec> register(@RequestBody FunctionSpec functionSpec) {
        // @RequestBody indica a Spring di convertire il JSON della richiesta HTTP nell'oggetto FunctionSpec
        // PASSO 1: Validazione delle invarianti di dominio (Rich Domain Model / Information Expert)
        // Interroghiamo direttamente FunctionSpec per verificare se la configurazione è valida
        if (!functionSpec.hasValidConfiguration()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        // PASSO 2: Verifica di conflitti nel registro H2
        // Se una funzione con questo nome è già registrata, restituiamo 409 (Conflict)
        if (registry.existsFunction(functionSpec.getName())) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        // PASSO 3: Creazione e attesa di avvio del container su Docker Swarm
        boolean isServiceSuccessfullyDeployed = functionProvider.deploy(functionSpec);
        if (!isServiceSuccessfullyDeployed) {
            // Se il deploy infrastrutturale fallisce, non persistiamo nulla su database per evitare inconsistenze
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } else {
            // PASSO 4: Persistenza a deploy completato (Pattern "Check prima di Salvare")
            registry.registerOrSaveFunction(functionSpec);
            return new ResponseEntity<>(functionSpec, HttpStatus.CREATED);
        }
    }
    // endpoint di invocazione della funzione (su nanofaas vero ci si collega ad altri moduli, qua semplifichiamo le cose)
    @PostMapping("/{name}:invoke")
    public ResponseEntity<String> invoke(@PathVariable("name") String name, @RequestBody(required = false) String payload) {
        // Controlliamo subito se la funzione esiste nel registry
        if (registry.existsFunction(name)) {
            // qui chiamiamo il FunctionDispatcher che contatterà il container Docker della funzione e restituirà l'output
            String response = dispatcher.dispatch(name, payload);
            return ResponseEntity.ok(response);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    @DeleteMapping("/{name}")
    public ResponseEntity<String> delete(@PathVariable("name") String name) {
        if (registry.existsFunction(name)) {
            // Pigliamo la function spec da H2
            if (!functionProvider.remove(name)) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                // Cancelliamo la funzione da H2
                registry.deleteFunction(name);
                return new ResponseEntity<>(HttpStatus.OK);
            }
        } else return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    // Esempio di invocazione HTTP: POST /v1/functions/{name}/scale?replicas=3
    @PostMapping("/{name}/scale")
    public ResponseEntity<String> update(
            @PathVariable("name") String name,
            @RequestParam(name = "replicas") int desiredNumberOfReplicas
    ) {
        // Validazione preliminare dei parametri: non consentiamo un numero negativo di repliche
        if (desiredNumberOfReplicas < 0) {
            return new ResponseEntity<>("Il numero di repliche non può essere negativo.", HttpStatus.BAD_REQUEST);
        }
        // Verifichiamo se la funzione esiste nel registro H2
        if (!registry.existsFunction(name)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        // PASSO 1: Aggiorniamo il numero di repliche su Docker Swarm
        boolean isSwarmScaleSuccessful = functionProvider.scale(name, desiredNumberOfReplicas);
        if (!isSwarmScaleSuccessful) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        // PASSO 2: Sincronizziamo lo stato aggiornato nel catalogo H2
        boolean isRegistryUpdated = registry.updateReplicas(name, desiredNumberOfReplicas);
        if (isRegistryUpdated) {
            return new ResponseEntity<>(HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
````

## File: ROADMAP.md
````markdown
# Roadmap & Diario di Bordo: mini_nanofaas

Documento di riferimento per tracciare lo stato di avanzamento del progetto, le decisioni architetturali, i concetti appresi e i passi futuri.

## 🎓 Filosofia & Modalità di Apprendimento
- **Approccio didattico**: Il progetto è un percorso di studio per imparare Spring Boot e l'architettura dei sistemi distribuiti con calma e metodo.
- **Ruolo dell'IA (Tutor)**:
  - **Non scrivere codice direttamente**: l'utente scrive e modifica il codice per consolidare l'apprendimento.
  - **Spiegare il "perché"**: illustrare chiaramente concetti, annotazioni e scelte architetturali di Spring Boot.
  - **Metodo maieutico/problem solving**: invece di dare subito la soluzione pronta, stimolare il ragionamento con domande guida, indizi e sfide di debug.

> [!IMPORTANT]
> **Promemoria di Fine Sessione (Aggiornamento Immagine Swarm)**:
> La pipeline CD su GitHub Actions (`docker-publish.yml`) è impostata su attivazione manuale (`workflow_dispatch`) e su tag di versione per non sprecare tempo e risorse ad ogni commit intermedio su `master`.  
> **A fine di ogni sessione di sviluppo**, quando il codice è stabile e pronto per essere testato sulla VM Swarm, ricordati di avviare manualmente il workflow su GitHub (*Actions > CD - Build & Publish Docker Image > Run workflow*) in modo da pubblicare l'ultima versione dell'immagine su GHCR!

---

## 📌 Obiettivo del Progetto
Costruire una piattaforma FaaS minimale (ispirata a **nanofaas**) con:
- **Stack tecnologico**: Java 25, Gradle (wrapper), Spring Boot 4.1.x, Docker Swarm.
- **I 4 componenti essenziali**:
  1. **Registro in memoria**: mappa nome → specifica (`FunctionSpec`).
  2. **API REST**: registrazione (`POST /v1/functions`) e invocazione (`POST /v1/functions/{name}:invoke`).
  3. **Swarm Provider**: gestione del ciclo vita dei container via Docker Swarm (`docker service create/scale/rm`).
  4. **Dispatcher**: inoltro HTTP della richiesta di invocazione alla funzione (`http://fn-<nome>:8080/invoke`).

---

## 🧭 Mappa dei Passi (Fasi)

### Fase 1: Il Control Plane Java & Metriche
- [x] **Passo 1.1**: Definizione del modello `FunctionSpec.java` (campi `name`, `imageDigest`, `replicas`, `timeoutSeconds`).
- [x] **Passo 1.2**: Correzione e strutturazione di `FunctionController.java` (`@RestController`, `@RequestMapping("/v1/functions")`, `ConcurrentHashMap`).
- [x] **Passo 1.3**: Implementazione registrazione `POST /v1/functions` con gestione conflitti (201 Created / 409 Conflict).
- [x] **Passo 1.4**: Scheletro endpoint di invocazione `POST /v1/functions/{name}:invoke` nel controller (gestione 404 Not Found).
- [x] **Passo 1.5**: Creazione del componente `FunctionDispatcher` con `RestClient` di Spring per inoltrare la chiamata HTTP a `http://fn-<name>:8080/invoke`.
- [x] **Passo 1.6**: Separazione del Registro in un `@Component` dedicato (`FunctionRegistry`) per mantenere il codice modulare e testabile.
- [x] **Passo 1.7**: Abilitare **Spring Boot Actuator** e **Micrometer Prometheus Registry** per esporre le metriche native e applicative su `/actuator/prometheus`.
- [x] **Passo 1.8**: Persistenza del catalogo funzioni con **Spring Data JPA** e database embedded **H2** (migrazione da `ConcurrentHashMap` a `@Entity` + `JpaRepository`).
- [x] **Passo 1.9**: Pipeline di CI con **GitHub Actions** (`ci.yml`):
  - Setup workflow su push e pull request verso `master`.
  - Configurazione runner Ubuntu con Java 25 (`actions/setup-java`) e caching nativo delle dipendenze Gradle.
  - Esecuzione automatica di test unitari (`./gradlew test`) e build (`./gradlew build`) per prevenire regressioni.
- [x] **Passo 1.10 (Sessione Refactoring IntelliJ #1 - Pulizia & Code Smells nel Control Plane)**:
  - *Obiettivo*: Imparare i fondamenti del refactoring assistito in IntelliJ IDEA con la rete di sicurezza della CI e dei test attivi.
  - *Funzionalità e shortcut IntelliJ*:
    - **Rename** (`Shift + F6`): rinomina sicura di classi, metodi, variabili e parametri con aggiornamento automatico dei riferimenti.
    - **Extract Variable / Constant / Field** (`Ctrl + Alt + V` / `C` / `F`): eliminazione di magic numbers/strings (es. porte, percorsi URL fissi).
    - **Extract Method** (`Ctrl + Alt + M`): scomposizione di metodi lunghi o con responsabilità multiple nel Controller o nel Dispatcher (`getUrl`).
    - **Inline** (`Ctrl + Alt + N`): eliminazione di variabili intermedie non necessarie.
    - **Change Signature** (`Ctrl + F6`): rimozione di wildcards non tipizzati (`ResponseEntity<?>` -> `ResponseEntity<String>`).
    - **Code Inspection & Optimize Imports** (`Ctrl + Alt + O` / `Ctrl + Alt + L`): pulizia di import morti e formattazione automatica.

---

### Fase 2: Ambiente Target & Provisioning (Multipass, cloud-init / Python & Swarm)
*Obiettivo: Creare prima l'ambiente isolato dove girerà l'intero cluster (Control Plane, Swarm, Rete Overlay e Prometheus).*
- [x] **Passo 2.1**: Provisioning automatico della VM con **Multipass**:
  - [x] Scrittura del file `infra/cloud-init.yaml` (installazione `docker.io`, permessi utente `ubuntu`, `systemctl enable --now docker`, `docker swarm init`, creazione overlay `nanofaas-net`).
  - [x] Risoluzione del blocco DHCP/firewalld su Fedora (abilitazione interfaccia bridge in zona `trusted`).
  - [x] Esecuzione lancio VM e verifica bootstrap (`docker info` e `docker network ls`).
- [x] **Passo 2.2**: Containerizzazione del Control Plane Spring Boot (`Dockerfile` multi-stage con Java 25):
  - [x] Scrittura del `Dockerfile` multi-stage (`eclipse-temurin:25-jdk` per il build e `eclipse-temurin:25-jre` per il runtime con CLI Docker inclusa) e `.dockerignore`.
  - [x] Esecuzione del container Spring Boot collegato alla rete overlay `nanofaas-net` e con mount del Docker socket (`/var/run/docker.sock`) per pilotare Swarm.
  - [x] `FunctionDispatcher` può risolvere via DNS `http://fn-<name>:8080/invoke` in modo nativo e trasparente.
- [x] **Passo 2.3**: Pipeline di CD con **GitHub Actions** & **GHCR** (`docker-publish.yml`):
  - [x] Workflow per il build automatico dell'immagine OCI multi-stage del Control Plane su attivazione manuale (`workflow_dispatch`) o tag di release (`v*.*.*`).
  - [x] Pubblicazione dell'immagine container su **GitHub Container Registry** (`ghcr.io/lucabasanisi/mini_nanofaas-controlplane`).
  - [x] Deploy disaccoppiato: la VM `nanofaas-vm` esegue `docker service create` scaricando l'immagine pronta senza compilare o avere JDK nel nodo.
- [ ] **Passo 2.4 (Evolutiva / Ottimizzazione Developer Experience)**: Pipeline **"Golden Image" Multipass** via GitHub Actions:
  - Workflow CI per "cuocere" (baking) e comprimere l'immagine disco della VM Multipass (`qcow2.xz` / `raw.gz`) con Ubuntu, Docker e Swarm già preinstallati e configurati.
  - Pubblicazione automatica come **Asset nelle GitHub Releases**.
  - Avvio istantaneo da qualsiasi macchina (anche laptop a basse risorse) tramite download dell'immagine pronta (`multipass launch file://$PWD/nanofaas-vm.qcow2`), azzerando i tempi di attesa di `apt` e download pacchetti al primo boot.
- [ ] **Passo 2.5 (Automazione Documentazione & Diagrammi Architetturali "Docs-as-Code")**:
  - *Trigger controllato*: integrato nel workflow CD esistente (`docker-publish.yml`) su tag di versione (`v*.*.*`) e `workflow_dispatch` (manuale), evitando esecuzioni continue ad ogni commit intermedio su master.
  - *Generazione Diagramma delle Classi (UMLDoclet + PlantUML)*: configurazione task Gradle con `nl.talsmasoftware:umldoclet` per estrarre le relazioni strutturali direttamente dal bytecode/JavaDoc in formato PlantUML (`.puml`).
  - *Generazione Diagramma C4 dei Componenti (Structurizr Java)*: scansione statica delle annotazioni Spring (`@RestController`, `@Service`, `@Repository`) tramite `SpringComponentFinderStrategy` ed esportazione automatica in standard C4-PlantUML.
  - *Rendering & Auto-commit*: compilazione in `.svg` via PlantUML CLI e commit automatico su Git dei diagrammi e del [`README.md`](file:///home/luca/IdeaProjects/mini_nanofaas/README.md) aggiornato prima del build Docker.

---

### Fase 3: Provider Swarm (Ciclo di Vita Funzioni)
- [x] **Passo 3.1**: Implementazione di `SwarmProvider.java`:
  - [x] Componente `@Service` per l'esecuzione di comandi Swarm CLI via `ProcessBuilder` (`docker service create --name fn-<nome> --network nanofaas-net --replicas <replicas> <immagine>`).
  - [x] Gestione sicura delle checked exception (`IOException`, `InterruptedException`) e controllo dell'exit code (`process.waitFor() == 0`).
  - [x] Integrazione in `FunctionController` via Constructor Injection con architettura "Check prima di Salvare" (check esistenza -> deploy Swarm -> persistenza H2 solo a deploy riuscito) per azzerare il database churn in caso di fallimenti infrastrutturali.
- [x] **Passo 3.2**: Readiness check (polling su endpoint `/health` del container funzione prima di considerare il servizio pronto):
  - [x] Implementazione metodo privato `waitForReadiness` in `SwarmProvider` con `RestClient` di Spring (`toBodilessEntity()`).
  - [x] Polling ciclico temporizzato con fallback su timeout configurabile o di default (operatore ternario) e assorbimento delle eccezioni transitorie di rete prima dell'apertura porta.
- [x] **Passo 3.3**: Implementazione scaling (`docker service scale`) e rimozione (`docker service rm`):
  - [x] Estrazione del metodo helper `executeCommand(ProcessBuilder)` in `DockerCliClient` (pattern DRY) e implementazione comandi CLI `removeService` (`docker service remove <name>`) e `scaleService` (`docker service update --replicas <N> <name>`).
  - [x] Coordinamento del ciclo di vita in `SwarmProvider`: incapsulamento della convenzione di naming con prefisso `fn-` (`remove` e `scale`).
  - [x] Sincronizzazione atomica dello stato in `FunctionRegistry`: cancellazione tramite `deleteById` e aggiornamento repliche in-place su H2 (`updateReplicas` con `findById` e `save`).
  - [x] Esposizione endpoint REST in `FunctionController`: `DELETE /v1/functions/{name}` e `POST /v1/functions/{name}/scale?replicas={n}` con corretta gestione degli status code HTTP (`200 OK`, `404 Not Found`, `500 Internal Server Error`).
- [x] **Passo 3.4 (Sessione Refactoring #2 - Analisi e Progettazione del Software: Code Smells & Design Pattern)**:
  - [x] **Debellamento Shotgun Surgery & Information Expert (GRASP)**:
    - Centralizzazione delle costanti di naming (`fn-`) e di routing (`/health`, `/invoke`, porta `8080`) direttamente dentro `FunctionSpec`.
    - Eliminazione del calcolo duplicato di stringhe e prefissi in `SwarmProvider`, `FunctionHealthChecker` e `FunctionDispatcher`.
  - [x] **Cura dello Code Smell "Data Class" (Martin Fowler)**:
    - Trasformazione di `FunctionSpec` da *Anemic Domain Model* (semplice DTO passivo) a *Rich Domain Model*.
    - Applicazione della tecnica **Move Method**: introduzione di `updateReplicaCount(int)` e `hasValidConfiguration()` per proteggere le invarianti di dominio (no repliche negative, stringhe non vuote, timeout strettamente positivo).
    - Aggiornamento di `FunctionRegistry` e `FunctionController` per delegare la validazione e la mutazione a `FunctionSpec`.
    - Suite di test unitari dedicata: `FunctionSpecTest.java` con JUnit 5 a protezione dei comportamenti osservabili.
  - [x] **Extract Interface & Dependency Inversion Principle (DIP / SOLID)**:
    - Estrazione dell'interfaccia `ProviderFunzione` da `SwarmProvider` con il contratto minimale di ciclo di vita (`deploy`, `remove`, `scale`).
    - Disaccoppiamento di `FunctionController` dal provider concreto Docker Swarm: il controller dipende ora unicamente dall'astrazione (`ProviderFunzione`), predisponendo l'architettura all'Open/Closed Principle (supporto trasparente a futuri provider come Kubernetes o Mock tramite `@ConditionalOnProperty`).

---

### Fase 4: Monitoring, Funzione Demo & Developer Tooling
- [ ] **Passo 4.1**: Creazione immagine funzione demo `test-greet` (con endpoint `/health` e `/invoke`).
- [ ] **Passo 4.2**: Deployment container **Prometheus** nella VM Multipass su `nanofaas-net`:
  - Configurazione dello scraping continuo su `http://control-plane:8080/actuator/prometheus`.
- [ ] **Passo 4.3**: Test end-to-end: registrazione da API -> creazione servizio Swarm -> invocazione -> verifica metriche su Prometheus.
- [ ] **Passo 4.4**: CLI Python per lo sviluppatore/operatore:
  - Comandi rapidi da host per impacchettare nuove funzioni, deployarle su `mini_nanofaas` e controllarne i log.

---

### Fase 5: Serverless Workflow Engine & Reti di Petri (WF-Nets, Verifica Formale & Pre-warming Predittivo)
*Obiettivo: Estendere la piattaforma dall'esecuzione di singole funzioni isolate all'orchestrazione formale di catene complesse di funzioni (Serverless Workflows). Modellare i workflow come Reti di Petri (WF-Nets di van der Aalst), garantendo la verifica formale di correttezza a deploy time (assenza di deadlock e token orfani) e abilitando il pre-warming predittivo dei container per azzerare il cold start.*

> [!TIP]
> **Valore Accademico & Originalità (Tesi & Ricerca)**:  
> Progettare un modulo che unisce le **WF-Nets di van der Aalst**, l'algoritmo di **verifica statica di Soundness** e il **Pre-warming infrastrutturale su Docker Swarm** colma un vuoto concreto nell'ecosistema FaaS:
> mentre i sistemi commerciali (AWS Step Functions, Google Cloud Workflows) si basano su macchine a stati proprietarie prive di garanzie formali e costringono a subire il cold start ad ogni step sequenziale, questa architettura offre garanzie matematiche di correttezza a tempo di deploy (zero bug/deadlock a runtime) e abbatte la latenza end-to-end scaldando i container in anticipo durante l'esecuzione del ramo precedente.

- [ ] **Passo 5.1 (Modello di Dominio per Reti di Petri & Workflow Spec)**:
  - Definizione del modello matematico a oggetti nel Control Plane: `Place`, `Transition` (associata a una `FunctionSpec`), `Arc` (con pesi), `Marking` (stato dei token/payload dati).
  - Definizione dell'astrazione di **WorkFlow Net (WF-Net)**: un unico posto di ingresso $i$ (source), un unico posto di uscita $o$ (sink), e ogni nodo della rete giace su un cammino da $i$ a $o$.
  - Esposizione endpoint REST: `POST /v1/workflows` per la registrazione dichiarativa del workflow (DSL JSON/YAML) e memorizzazione nel catalogo.
- [ ] **Passo 5.2 (Verifica Statica di Soundness al Deploy - Zero Bug a Runtime)**:
  - Implementazione dell'algoritmo di verifica formale a tempo di deploy prima dell'accettazione del workflow:
    1. **Assenza di Deadlock**: per ogni marcatura raggiungibile da $i$, è sempre possibile raggiungere la marcatura finale $o$.
    2. **Proper Completion (Assenza di Token Orfani / Unboundedness)**: al raggiungimento del sink $o$, non devono rimanere token pendenti in altri posti della rete.
    3. **Assenza di Transizioni Morte**: ogni funzione all'interno del workflow deve poter scattare in almeno uno scenario di esecuzione valido.
  - Rifiuto immediato del deploy con errore descrittivo e controesempio se la verifica di Soundness fallisce, proteggendo il cluster da fork-bomb e stalli.
- [ ] **Passo 5.3 (Motore di Esecuzione & Firing Asincrono Concorrente)**:
  - Implementazione del workflow runner (`WorkflowExecutionEngine`): istanziazione di una corsa del workflow con generazione dell'ID istanza e piazzamento del token iniziale nel posto $i$ con payload JSON.
  - Ciclo di firing: rilevamento delle transizioni abilitate (posti di input con gettoni disponibili), estrazione dei dati e invocazione concorrente tramite `FunctionDispatcher`.
  - Gestione nativa dei pattern di controllo: **Sequence**, **AND-Split / AND-Join** (Fork/Join parallelo asincrono con sincronizzazione barriera), **XOR-Split / XOR-Join** (scelta condizionale basata sul payload di risposta).
- [ ] **Passo 5.4 (Pre-warming Predittivo su Docker Swarm)**:
  - Analisi predittiva della marcatura a runtime: non appena una transizione $T_n$ è in esecuzione, il workflow engine ispeziona gli archi uscenti verso i posti successivi e identifica le transizioni target $T_{n+1}$ che diventeranno abilitate al termine.
  - Chiamata proattiva al provider infrastrutturale (`ProviderFunzione.scale("fn-target", 1)`) per riscaldare il container durante il tempo di calcolo della funzione corrente.
  - Misurazione della riduzione della latenza end-to-end e azzeramento del cold start lungo la catena di workflow rispetto a piattaforme serverless commerciali.
- [ ] **Passo 5.5 (Tracciamento, Audit & Visualizzazione dello Stato del Workflow)**:
  - Tracciamento della sequenza di marcatura e audit trail su database per ogni istanza di workflow (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`).
  - Endpoint REST `GET /v1/workflows/instances/{instanceId}` per ispezionare la marcatura corrente e la cronologia degli stati.
  - Esportazione dinamica del grafo di esecuzione in formato Graphviz / DOT / Mermaid per visualizzazione grafica dei token in movimento.

---

### Fase 6: Resilienza Distribuita, Concorrenza & Doppia Sorgente di Verità
*Obiettivo: Gestire la concorrenza spinta (race conditions, spam di richieste delete/scale), la tolleranza ai guasti nelle chiamate remote e la consistenza eventuale tra il Database locale (H2/PostgreSQL) e l'infrastruttura esterna (Docker Swarm).*
- [ ] **Passo 6.1 (Cancellazione & Operazioni Idempotenti)**:
  - Gestione sicura del fallimento della CLI/API Docker quando una risorsa è già stata eliminata (*No such service* non deve bloccare la pulizia del database).
  - Garantire l'idempotenza degli endpoint (`DELETE /v1/functions/{name}` restituisce esito positivo se la funzione cessa di esistere, indipendentemente da quanti tentativi concorrenti arrivino).
- [ ] **Passo 6.2 (Gestione Race Conditions, Concorrenza & Locking Granulare)**:
  - Protezione dalle race condition su invocazioni contemporanee di `register`, `scale` e `delete` sullo stesso nome di funzione.
  - Implementazione di optimistic locking con `@Version` su `FunctionSpec` in JPA per rilevare conflitti di concorrenza sui metadati.
  - Meccanismo di locking per chiave/funzione (es. `Striped Lock` o lock nominati in memoria) per impedire operazioni concorrenti e distruttive sulla medesima funzione (es. deploy in corso vs delete simultaneo).
- [ ] **Passo 6.3 (Circuit Breaker, Retries & Fallback nel Dispatcher)**:
  - Protezione del Control Plane contro effetti a cascata (*cascading failures*) e saturazione dei thread HTTP quando i container delle funzioni sono lenti o in crash.
  - Introduzione del pattern **Circuit Breaker** su `FunctionDispatcher` (stato *Closed*, *Open*, *Half-Open*) con tempi di apertura e chiusura configurabili.
  - Policy di **Retry con Backoff esponenziale & Jitter** per assorbire anomalie transitorie di rete.
  - Risposta di fallback con codici HTTP precisi (`503 Service Unavailable` o `504 Gateway Timeout`) e header informativi.
- [ ] **Passo 6.4 (Reconciliation Loop / Pattern Riconciliatore & Self-Healing)**:
  - Background task periodico (`@Scheduled` di Spring Boot, ispirato all'architettura a controller di Kubernetes).
  - Algoritmo di riconciliazione a 3 vie tra stato desiderato (H2) e stato effettivo (Docker Swarm):
    1. *Funzione nel catalogo ma assente in Swarm*: ri-creazione automatica del servizio Swarm o transizione a stato `DEGRADED`.
    2. *Servizio in Swarm ma assente nel catalogo*: garbage collection automatica dei container/servizi orfani via `docker service rm`.
    3. *Discrepanza di repliche*: allineamento automatico delle repliche attive rispetto a quelle configurate a catalogo.
  - Esportazione su Prometheus delle metriche del riconciliatore (durata del ciclo, discrepanze rilevate, azioni correttive applicate).
- [ ] **Passo 6.5 (Macchina a Stati della Funzione & Transazioni Compensative)**:
  - Definizione degli stati del ciclo di vita: `PENDING`, `READY`, `DEGRADED`, `SCALING`, `TERMINATING`.
  - Transazioni compensative (Saga minimale): se la creazione del servizio Swarm ha successo ma il readiness check va in timeout irreversibile, scatta il rollback infrastrutturale automatico per non lasciare container zombie.

---

### Fase 7: Architettura DDBMS & Stateful Serverless: Persistenza Distribuita & Ripresa dello Stato al Crash del Nodo
*Obiettivo: Trasformare `mini_nanofaas` da piattaforma serverless puramente stateless a un vero e proprio DDBMS distribuito per Stateful Functions. Garantire che lo stato applicativo di una funzione sia replicato su cluster multi-nodo e possa essere ripreso istantaneamente e senza perdita di dati (Zero Data Loss / RPO=0) quando il nodo fisico o virtuale su cui gira il container subisce un crash improvviso.*
- [ ] **Passo 7.1 (Topologia Swarm Multi-Nodo & Cluster DDBMS Replicato)**:
  - Estensione dell'ambiente locale con Multipass per creare un cluster Docker Swarm multi-nodo (1 Manager + 2 Worker collegati su `nanofaas-net`).
  - Deployment di un engine DDBMS distribuito su Swarm con replica attiva e tolleranza ai partizionamenti (es. cluster Redis Sentinel / Redis Cluster con replica master-replica e persistenza AOF su volumi indipendenti, oppure store distribuito basato su Raft come TiKV/etcd).
  - Bisogna approfondire anche altre soluzioni, come: Xata e StarbaseDB, anche se forse è meglio per rendere le funzioni non più effimere (che ora non centra nulla). Inoltre sono soluzioni non totalmente open-source oppure richiedono iscrizioni a servizi terzi. Sarebbe comunque figo implementare da zero una cosa del genere e renderla open-source.
  - Definizione del modello di consistenza distribuita (Quorum writes, Read-Your-Writes e durability guarantees).
- [ ] **Passo 7.2 (Pattern Stateful Functions & Checkpointing / Write-Ahead Logging)**:
  - Definizione del ciclo di vita dello stato della funzione: astrazione di *Stateful Entity / Virtual Actor* (chiave di partizionamento per-funzione o per-istanza: `fn:<name>:<key>`).
  - Strategia a doppio livello di persistenza:
    1. **Write-Ahead Logging (WAL)**: ogni mutazione dello stato generata durante l'invocazione (`POST :invoke`) viene committata atomicamente sul cluster DDBMS prima di inviare il `200 OK` al client.
    2. **Snapshotting periodico**: compattazione e serializzazione periodica dello stato in-memory per accelerare i tempi di recupero ed evitare replay infiniti dei log.
- [ ] **Passo 7.3 (Fencing Tokens, Lease Distribuiti & Protezione Split-Brain)**:
  - Gestione dei *network partition* e *grey failures* (nodo creduto morto dall'orchestratore, ma ancora attivo e in grado di scrivere).
  - Implementazione del meccanismo di **Fencing Token** (monotonically increasing versioning o lease distribuiti con TTL sul DDBMS).
  - Rifiuto categorico da parte del DDBMS di qualsiasi operazione di scrittura proveniente da istanze "zombie" con token obsoleto, prevenendo divergenze o corruzione silente dello stato.
- [ ] **Passo 7.4 (Node Crash Detection, Swarm Task Rescheduling & State Rehydration)**:
  - Simulazione del crash hardware o arresto improvviso di un worker Swarm (`multipass stop nanofaas-worker-1`).
  - Docker Swarm rileva il mancato heartbeat del nodo e ripianifica (`reschedule`) il container della funzione su un worker superstite.
  - Procedura di **State Rehydration**: al boot sul nuovo nodo, la funzione/sidecar interroga il DDBMS durante il readiness check, ricarica l'ultimo snapshot coerente, riapplica i log del WAL non consolidati e notifica la disponibilità al traffico.
- [ ] **Passo 7.5 (Deduplicazione Invocazioni & Semantica Transazionale Exactly-Once)**:
  - Gestione delle chiamate HTTP in-flight al momento esatto del crash del nodo.
  - Implementazione di **Idempotency Keys** passate dal chiamante o generate dal Dispatcher (`X-Idempotency-Key` salvata con TTL nel DDBMS).
  - Se il client riprova l'invocazione verso la nuova istanza della funzione, il DDBMS rileva la chiave già processata e restituisce la risposta registrata senza rieseguire la logica di business né alterare nuovamente lo stato (semantica *effectively-once*).

---

### Fase 8: Chaos Engineering, Fault Injection & Stress Testing
*Obiettivo: Verificare empiricamente sul campo l'efficacia dei meccanismi di resilienza, la convergenza del Reconciliation Loop, la tenuta del DDBMS e il ripristino istantaneo dello stato al crollo di nodi fisici nello Swarm.*
- [ ] **Passo 8.1 (Chaos Infrastrutturale: Container Kill, Service Termination & Node Eviction Out-of-Band)**:
  - Iniezione di guasti esterni: uccisione violenta di container (`docker kill`), rimozione diretta di servizi Swarm e spegnimento brutale di interi nodi del cluster (`multipass stop nanofaas-worker-1`).
  - Verifica empirica: Swarm ripianifica il servizio sul nodo superstite, il *Reconciliation Loop* corregge le discrepanze, e la funzione ripristina il proprio stato dal DDBMS senza alcuna perdita (*Zero Data Loss verification*).
- [ ] **Passo 8.2 (Chaos di Rete: Latenza, Packet Loss & Partizioni sulla Rete Overlay)**:
  - Simulazione di degrado della rete overlay `nanofaas-net` (tramite `tc` / `iptables` / Toxiproxy o Pumba): iniezione di latenza artificiale (es. 2000ms), perdita di pacchetti e partizioni parziali tra Control Plane, container funzioni e cluster DDBMS.
  - Validazione empirica del Circuit Breaker e dei Fencing Tokens sotto isolamento di rete (verifica assenza di split-brain nel DDBMS).
- [ ] **Passo 8.3 (Concurrency Fuzzing & Stress Test sotto Carico Massivo)**:
  - Test di carico ad alta concorrenza (es. con script Python / k6 / wrk): bombardamento simultaneo di `register`, `scale`, `delete` e `invoke` su centinaia di thread paralleli con mutazioni di stato continue.
  - Verifica della tenuta dei lock e delle transazioni DDBMS: assenza di deadlock nel Control Plane, persistenza integra in H2 e nel DDBMS (nessun dato corrotto o record fantasma).
- [ ] **Passo 8.4 (Crash & Cold Recovery del Control Plane)**:
  - Simulazione di riavvio improvviso del nodo manager o crash del Control Plane (`kill -9`) durante operazioni intensive di scale o registrazione.
  - Bootstrap recovery: all'avvio successivo, il Control Plane deve re-sincronizzare lo stato H2 con Swarm e col DDBMS senza cancellare per errore servizi attivi e sani.
- [ ] **Passo 8.5 (Osservabilità del Chaos: Metriche MTTR, RPO/RTO & Dashboard di Resilienza)**:
  - Monitoraggio in tempo reale durante i test di chaos tramite Prometheus: misurazione di MTTR (*Mean Time to Recovery*), RTO (*Recovery Time Objective*), verifica RPO=0 (*Recovery Point Objective*), tasso di fallimento invocazioni, saturazione pool di connessioni e memoria JVM.
  - Definizione di SLO (Service Level Objectives) minimi di resilienza per la piattaforma.

---

### Fase 9: Ottimizzazione Prestazioni, Cold Start & JVM Next-Gen: AOT Cache (Leyden) e CRaC
*Obiettivo: Esplorare le frontiere dell'avvio istantaneo della JVM per eliminare il problema del Cold Start nelle piattaforme Serverless/FaaS sia lato Control Plane che lato funzioni utente.*
- [ ] **Passo 9.1 (AOT Cache sul Control Plane)**:
  - [ ] Comprensione del modello a due fasi di **Project Leyden / JEP 516** (`-XX:AOTMode=record` durante la "training run" e `-XX:AOTMode=on` a runtime).
  - [ ] Aggiornamento del `Dockerfile` multi-stage del Control Plane per eseguire una corsa di addestramento (context startup + readiness check e shutdown automatico) producendo l'archivio pre-risolto delle classi (`.aot`).
  - [ ] Misurazione del tempo di avvio (`Started MiniNanofaasApplication in X seconds`) e della memoria residente (RSS) prima e dopo l'abilitazione della AOT Cache.
- [ ] **Passo 9.2 (AOT Cache per il Runtime delle Funzioni FaaS)**:
  - [ ] Creazione del template base per funzioni serverless Java con AOT Cache pre-addestrata.
  - [ ] Abbattimento dei tempi di readiness check (`waitForReadiness` in `SwarmProvider`) durante lo scale-up o cold start on-demand.
  - [ ] Verifica del mantenimento delle caratteristiche JVM standard: profilazione con Java Flight Recorder (JFR) e compilazione JIT ad alte prestazioni a regime continuo.
- [ ] **Passo 9.3 (Sperimentazione CRaC - Coordinated Restore at Checkpoint)**:
  - [ ] Studio dell'architettura **CRaC** basata su CRIU (Linux Checkpoint/Restore in Userspace) supportata da Azul Zulu / OpenJDK CRaC.
  - [ ] Gestione del ciclo di vita con l'interfaccia `org.crac.Resource` (`beforeCheckpoint` e `afterRestore`) per chiudere/riaprire connessioni di rete (es. socket Swarm, database, client HTTP).
  - [ ] Creazione di uno snapshot di una funzione pre-riscaldata e ripristino istantaneo sub-100ms.
- [ ] **Passo 9.4 (Analisi Comparativa Prestazioni & Trade-off Architetturali)**:
  - [ ] Benchmark comparativo su 3 dimensioni (Tempo di avvio al primo invoke, Consumo memoria RSS al boot, Picco di throughput JIT):
    - JVM Standard Vanilla (Java 25)
    - JVM con AOT Cache (Project Leyden / JEP 516)
    - JVM con Snapshot Restore (CRaC)
    - GraalVM Native Image (confronto teorico/pratico su closed-world assumption e assenza JIT)
  - [ ] Stesura del report di sintesi nel diario di bordo con raccomandazioni per ambienti FaaS.

---

## 📖 Concetti Chiave Appresi & Decisioni

1. **`ConcurrentHashMap` vs `HashMap`**:
   - I server web gestiscono le richieste HTTP su thread paralleli (pool di thread).
   - `HashMap` non è thread-safe e può causare race condition e corruzione della memoria.
   - `ConcurrentHashMap` permette letture e scritture sicure senza bloccare l'intero server.

2. **Convenzioni REST & Status Code**:
   - `201 Created`: restituito quando una risorsa (la funzione) viene creata con successo.
   - `409 Conflict`: restituito se si tenta di registrare una funzione con un nome già esistente.
   - `404 Not Found`: restituito se si tenta di invocare o cancellare una funzione non presente nel registro.

3. **Routing in Spring Boot**:
   - `@RestController`: dichiara che i metodi restituiscono dati serializzati in JSON.
   - `@RequestMapping("/v1/functions")`: prefisso comune URL.
   - `@PathVariable("name")`: cattura variabili dinamiche dall'URL (`/v1/functions/{name}:invoke`).
   - `@RequestBody`: deserializza il payload HTTP in ingresso.

4. **Inversion of Control & Constructor Injection**:
   - I componenti (`@Component`, `@Repository`, `@RestController`) non istanziano manualmente i propri collaboratori con `new`.
   - Vengono dichiarati come campi `private final` e passati come parametri nel costruttore.
   - Spring Boot risolve automaticamente le dipendenze prendendo i Bean dal suo magazzino (`ApplicationContext`).

5. **`RestClient` vs `RestClient.Builder`**:
   - Spring Boot non crea un Bean `RestClient` già predefinito per lasciare flessibilità sulle configurazioni.
   - Fornisce invece un `RestClient.Builder` preconfigurato con i convertitori JSON di Spring, da cui generare l'istanza con `.build()`.

6. **Separation of Concerns (SoC) & `FunctionRegistry`**:
   - Il Controller deve occuparsi esclusivamente della gestione delle richieste HTTP (routing, status code, validazione input).
   - La logica di persistenza e memorizzazione è delegata a un componente specializzato (`@Repository` `FunctionRegistry`), rendendo il codice disaccoppiato, modulare e facilmente testabile.
   - L'uso di `putIfAbsent` e `Optional<T>` garantisce thread-safety e gestione pulita dei valori assenti.

7. **Decisioni di Persistenza & Architettura Dati**:
   - **Registro Funzioni (Control Plane)**: uso di **H2** (embedded) per lo sviluppo locale e didattico con Spring Data JPA; transizione naturale a **PostgreSQL** per ambienti multi-istanza o produzione.
   - **Stato Funzioni (Stateful Functions)**: uso di **Redis** come key-value store ultra-veloce, condiviso via overlay network tra i container/sidecar Watchdog per memorizzare lo stato applicativo con latenze minime e supporto TTL.

8. **Provisioning Infrastrutturale: `cloud-init` vs Terraform/Ansible**:
   - **Terraform/Ansible**: potenti per parchi macchine distribuiti e multi-cloud, ma rappresentano overengineering e dispersione di focus per una singola VM locale di sviluppo.
   - **`cloud-init`**: standard de facto per il bootstrap dichiarativo delle istanze Linux al primo avvio. Permette di automatizzare pacchetti Docker, configurazione e comandi Swarm con un singolo file YAML pulito, supportato nativamente da Multipass (`multipass launch --cloud-init ...`) senza dipendenze esterne.

9. **CI/CD & Container Registry (GitHub Actions + GHCR)**:
   - **Separazione Build vs Runtime**: l'ambiente di runtime (VM Multipass / Swarm) non deve contenere compilatori, JDK completi o codice sorgente; deve solo eseguire artefatti immutabili.
   - **Feedback immediato**: la pipeline di CI garantisce che il codice compili e i test passino su un ambiente pulito prima di ogni merge.
   - **GHCR (GitHub Container Registry)**: fornisce un registry OCI sicuro e integrato nativamente nel repository GitHub per distribuire le immagini Docker del Control Plane.

10. **Spring Boot Actuator & Micrometer Prometheus**:
   - **Separazione tra raccolta ed esportazione**: Spring Boot Actuator raccoglie metriche interne e di sistema (JVM, CPU, HTTP requests) tramite **Micrometer**, mentre il registro Prometheus (`micrometer-registry-prometheus`) formatta queste metriche nello standard plain-text atteso dai server Prometheus.
   - **Sicurezza e Web Exposure**: per evitare di esporre informazioni sensibili di sistema, Actuator nasconde di default quasi tutti gli endpoint web tranne `/actuator/health`. Vanno esposti selettivamente con `management.endpoints.web.exposure.include=health,prometheus`.
   - **Auto-configuration & Bean Injection**: quando un componente richiede una dipendenza (come `RestClient.Builder`), Spring tenta di iniettarla; se non disponibile automaticamente, la si può definire e registrare nel contesto applicativo annotando un metodo produttore con `@Bean`.

11. **Persistenza con Spring Data JPA ed Entità**:
   - **Mappatura Oggetto-Relazionale (`@Entity`)**: annotando una classe con `@Entity` e identificando la chiave primaria con `@Id`, Hibernate mappa automaticamente la classe su una tabella del database e ne gestisce il ciclo di vita.
   - **Astrazione CRUD con `JpaRepository`**: estendendo `JpaRepository<FunctionSpec, String>`, Spring Data genera a runtime le implementazioni di metodi come `save`, `findById`, `existsById` ed `findAll` senza dover scrivere query SQL manuali.
   - **H2 Database in-memory**: database embedded ideale per test veloci e sviluppo locale isolato, rimpiazzando mappe volatili in memoria come `ConcurrentHashMap` con una vera semantica transazionale ACID.
   - **Pattern Repository & Disaccoppiamento**: mantenere il Controller isolato dai dettagli di persistenza (interfacciandosi solo con un servizio/registro dedicato) permette di cambiare il motore di memorizzazione sottostante a costo zero per l'API.

12. **Refactoring Assistito con l'IDE & Code Smells**:
   - **Code Smell vs Bug**: un code smell non è un errore di compilazione o di esecuzione, ma un sintomo di accoppiamento o bassa coesione (es. violazione del Single Responsibility Principle isolando `getUrl`, o firme con wildcard non sicure `ResponseEntity<?>`).
   - **Automazione & Safe Refactoring**: strumenti dell'IDE come *Rename* (`Shift + F6`), *Extract Method* (`Ctrl + Alt + M`), *Inline* (`Ctrl + Alt + N`) e *Change Signature* (`Ctrl + F6`) aggiornano atomicamente dichiarazioni e chiamate in tutto il progetto, azzerando errori manuali e lavorando in sinergia con la rete di sicurezza della CI.

13. **Runtime Provisioning (`cloud-init`) vs Golden Image (Image Baking)**:
   - **Runtime Provisioning (`cloud-init`)**: approccio snello per il repository (richiede solo un file YAML dichiarativo leggero), ma sposta il tempo di download dei pacchetti (`docker.io`) e la configurazione al primo boot sul client locale, dipendendo da CPU e banda della macchina host.
   - **Golden Image (Pre-baked VM)**: la pipeline CI (GitHub Actions) esegue il bootstrap una volta per tutte su runner cloud veloci e produce un'immagine disco immutabile e compressa distribuita via Release Asset. Il client locale deve solo decomprimerla e avviarla con Multipass (`multipass launch file://...`) con tempo di boot immediato (~5-10 secondi) e zero carico computazionale su portatili poco potenti.

14. **Integrazione di Processi di Sistema con `ProcessBuilder`**:
   - **Sicurezza & Argomenti Separati**: passare i comandi come lista di stringhe evita il passaggio da una shell intermedia (`/bin/sh`), proteggendo l'applicazione da vulnerabilità di command injection e problemi di quoting.
   - **Ciclo di vita & Exit Code**: `ProcessBuilder.start()` lancia il processo in background restituendo un `Process`; `process.waitFor()` blocca il thread in attesa del completamento e restituisce l'exit code numerico (`0` successo).
   - **Gestione Checked Exceptions**: obbligo di gestire `IOException` (eseguibile non trovato, permessi socket mancanti) e `InterruptedException` (thread interrotto durante l'attesa del processo esterno).

15. **Strategie di Consistenza e Prevenzione Database Churn**:
   - **Database Churn**: se un sistema esterno (Swarm) è temporaneamente guasto o sovraccarico, eseguire prima la scrittura nel database locale (`INSERT`) e poi un rollback (`DELETE`) consuma I/O e sporca lo stato inutilmente.
   - **Pattern "Check -> Esegui -> Salva"**: verificare l'univocità con una query leggera in lettura (`existsById`), tentare l'azione esterna rischiosa (deploy del container) e persistere su database solo in caso di esito positivo garantisce consistenza naturale a zero overhead di pulizia.

16. **Readiness Probe & Polling su Sistemi Distribuiti Asincroni**:
   - **Asincronia dell'orchestratore**: comandi come `docker service create` terminano con successo non appena il job è pianificato, ma l'avvio effettivo del container e dell'app sulla porta `8080` richiede tempo.
   - **HTTP Health Polling con `RestClient`**: l'interrogazione periodica di un endpoint dedicato (`/health`) con `toBodilessEntity()` evita fallimenti di invocazione immediata (`Connection Refused`) e garantisce che il servizio sia pienamente operativo prima di restituire `201 Created`.
   - **Fallback & Operatore Ternario (`? :`)**: gestione flessibile del timeout con valore configurabile per-funzione e default difensivo (`timeoutSeconds > 0 ? timeoutSeconds : 5`).

17. **Docs-as-Code & Diagrammi Architetturali Automatici**:
   - **Disallineamento Documentazione (Doc Rot)**: mantenere diagrammi e schemi aggiornati a mano porta inevitabilmente a divergenza rispetto al codice reale.
   - **UMLDoclet per Class Diagrams**: sfrutta il compilatore Java (`javadoc`) per estrarre la gerarchia delle classi, campi e metodi direttamente dal sorgente in sintassi PlantUML.
   - **Structurizr & C4 Model per Spring Boot**: tramite `SpringComponentFinderStrategy`, analizza i componenti Spring (`@RestController`, `@Service`, `@Repository`) estraendo le relazioni tra bean e generando un diagramma C4 di livello 3 (Component) esportato in formato C4-PlantUML.
   - **Integrazione mirata in CD**: generare e committare i diagrammi solo nel workflow di rilascio (`docker-publish.yml` su tag `v*.*.*` o `workflow_dispatch`) mantiene la documentazione e il `README.md` sempre sincronizzati con le versioni ufficiali senza appesantire la CI ordinaria di sviluppo.

18. **AOT Cache (Project Leyden / JEP 516) vs GraalVM vs CRaC**:
   - **Il dilemma del Serverless in Java**: le piattaforme FaaS soffrono del "Cold Start" a causa del tempo speso dalla JVM per caricare centinaia di classi, verificare il bytecode e inizializzare i framework (Spring Boot / Micronaut / Quarkus).
   - **Project Leyden & AOT Cache (HotSpot nativo)**:
     - Tramite una *training run* (`-XX:AOTMode=record`), la JVM esegue l'applicazione registrando le classi caricate, i metadati e il codice pre-risolto in un archivio su disco.
     - Nei boot successivi (`-XX:AOTMode=on`), la JVM mappa direttamente l'archivio in memoria (memory-mapped file), dimezzando i tempi di avvio senza rinunciare a JIT, reflection dinamica o agenti di monitoraggio (JFR).
   - **CRaC (Coordinated Restore at Checkpoint)**:
     - Sfrutta Linux CRIU per "congelare" l'intero heap e stato della JVM già riscaldata e avviata, salvandolo su disco.
     - Al risveglio (restore), l'applicazione riparte in poche decine di millisecondi (sub-50ms) dallo stato esatto in cui era stata congelata. Richiede gestione attiva delle risorse (`org.crac.Resource`) per chiudere e riaprire socket di rete o connessioni database prima del freeze.
   - **GraalVM Native Image**: compila in codice macchina AOT a tempo di build sotto la *closed-world assumption*, garantendo boot istantaneo e footprint minimo, ma sacrificando la compilazione JIT dinamica a lungo termine e richiedendo configurazioni esplicite per reflection e bytecode dynamic loading.

19. **Ciclo di Vita & Pattern DRY nella CLI Docker (`DockerCliClient`)**:
    - **Centralizzazione dell'esecuzione comandi**: estrazione di un metodo helper (`executeCommand`) per evitare la duplicazione della gestione dei thread, `ProcessBuilder.start()`, `process.waitFor()` e delle checked exception (`IOException`, `InterruptedException`).
    - **Argomenti posizionali vs flag Docker**: la CLI Docker distingue nettamente tra argomenti posizionali (`docker service remove <name>`, `docker service update ... <name>`) e flag (`--name`, `--replicas`).

20. **Aggiornamento di Stato e Semantica `JpaRepository`**:
    - **Ciclo di vita dell'entità e persistenza in H2**: recupero dell'oggetto da `findById(id)`, modifica in memoria dei campi (`setReplicas`) e persistenza atomica tramite `save()` per sincronizzare lo stato applicativo nel database relazionale.

21. **Semantica REST per Operazioni di Ciclo di Vita (Scaling & Deletion)**:
    - **Verbo `DELETE` su Risorse Univoche**: l'eliminazione si modella canonicamente con `@DeleteMapping("/{name}")` e `@PathVariable`, restituendo `200 OK` (o `204 No Content`) a successo o `404 Not Found` se inesistente.
    - **Sottomisorse & Operazioni di Scaling**: endpoint dedicato `POST /v1/functions/{name}/scale?replicas={n}` con `@RequestParam int replicas` per separare il routing dai parametri operativi.

22. **Architettura DDBMS & Stateful Serverless (Crash del Nodo e Ripristino dello Stato)**:
    - **Stateless vs Stateful FaaS**: nei sistemi FaaS canonici i container sono effimeri e privi di memoria; nei sistemi stateful il container è solo un'unità di computazione rimpiazzabile, mentre l'identità e lo stato risiedono in un cluster DDBMS condiviso, distribuito e replicato.
    - **State Rehydration**: alla ripartenza di una funzione su un nuovo nodo Swarm dopo la morte del worker precedente, il runtime esegue una query di reidratazione caricando l'ultimo snapshot valido e il Write-Ahead Log (WAL) prima di accettare traffico.
    - **Fencing Tokens & Partizioni di Rete**: in caso di isolamento parziale di rete, Swarm potrebbe avviare un nuovo container credendo che il vecchio sia morto (grey failure); i Fencing Token (contatori monotonici verificati dal DDBMS) garantiscono che solo l'istanza con il lease attivo possa scrivere sul database, neutralizzando tentativi di scrittura da nodi zombie e prevenendo split-brain.

23. **Analisi e Progettazione del Software: Code Smells & Refactoring Avanzato**:
    - **Shotgun Surgery vs Divergent Change (Martin Fowler)**:
      - *Shotgun Surgery*: una singola decisione di dominio (es. prefisso container `fn-`, porte di rete o URL) costringe a modificare molte classi diverse. Si risolve identificando l'**Information Expert (GRASP)** e accentrando costanti e regole di calcolo (es. in `FunctionSpec`).
      - *Divergent Change*: una singola classe cambia continuamente per motivi disparati (violazione dell'SRP).
    - **Data Class vs Rich Domain Model**:
      - Una classe con soli campi, getter e setter passivi rappresenta lo smell *Data Class* e induce *Feature Envy* nelle classi che la manipolano.
      - Applicando la tecnica **Move Method**, si spostano la validazione delle invarianti (`hasValidConfiguration`) e le mutazioni protette di business (`updateReplicaCount`) dentro la classe stessa, impedendo stati invalidi o incoerenti a runtime.
    - **Dependency Inversion Principle (DIP) & Extract Interface**:
      - I componenti di alto livello (`FunctionController`) non devono dipendere da dettagli di basso livello (`SwarmProvider`), ma da un'astrazione (`ProviderFunzione`).
      - *Gestione di implementazioni multiple in Spring Boot*: l'aggiunta di ulteriori provider (es. Kubernetes) viene gestita in modo disaccoppiato tramite `@ConditionalOnProperty(name = "nanofaas.provider", havingValue = "...")` o profili `@Profile`, preservando l'Open/Closed Principle (OCP) ed evitando `NoUniqueBeanDefinitionException`.

24. **Serverless Workflow Orchestration & Reti di Petri (WF-Nets di van der Aalst)**:
    - **Valore per una Tesi o Ricerca di Alto Profilo**: progettare un modulo che unisce le WF-Nets di van der Aalst, l'algoritmo di verifica statica di Soundness e il Pre-warming infrastrutturale getta un ponte inedito tra Informatica Teorica (Metodi Formali) e Ingegneria del Software (Sistemi Distribuiti & Cloud).
    - **Limiti delle FSM nei Workflow Concorrenti**: le macchine a stati tradizionali (es. AWS Step Functions / ASL) soffrono di complessità ed esplosione combinatoria quando devono orchestrare rami paralleli asincroni (Fork/Join). Le Reti di Petri modellano la concorrenza distribuita in modo nativo tramite marcature locali e gettoni (tokens).
    - **Teoria della Soundness (Correttezza Formale)**: una WF-Net è *Sound* se e solo se garantisce terminazione pulita (nessun token residuo), assenza di deadlock (lo stato finale è sempre raggiungibile) e assenza di transizioni morte. Verificare la Soundness a tempo di deploy impedisce bug logici, corse critiche o fork-bomb prima dell'avvio dei container.
    - **Pre-warming Predittivo basato sulla Marcatura**: conoscendo la topologia della rete e la marcatura corrente, il Control Plane può anticipare deterministicamente quali funzioni diventeranno abilitate al firing, pre-riscaldando i container (`scale(1)`) durante il calcolo dello step precedente e azzerando il cold start.

---

## 🔮 Fasi Future & Ricerca Avanzata
*(Nota: la gestione delle Stateful Functions con DDBMS distribuito è stata promossa a Fase 7 attiva del progetto).*
- **Model Checking LTL su Workflow**: integrazione di un monitor formale per verificare formule in Linear Temporal Logic (LTL / LTL_f) sui grafi di esecuzione delle WF-Nets (es. verifica automatica del pattern Saga e delle transazioni di compensazione).
- **Event-Driven Architecture**: integrazione con broker distribuiti (Apache Kafka o NATS JetStream) per abilitare invocazioni asincrone su code di messaggi ed event streams ad altissimo throughput.
- **WebAssembly (Wasm) Runtime**: supporto a moduli Wasm (es. Wasmtime / Wasmer) per esecuzioni serverless multi-linguaggio con cold start sub-millisecondo e footprint di memoria microscopico (<5MB).
- **Federazione Multi-Cluster & Edge Orchestration**: deployment geograficamente distribuito (Edge-to-Cloud) con replica attiva-attiva del catalogo e del DDBMS tra cluster Swarm multipli.
````
