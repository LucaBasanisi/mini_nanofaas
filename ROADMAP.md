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

### Fase 5: Resilienza Distribuita, Concorrenza & Doppia Sorgente di Verità
*Obiettivo: Gestire la concorrenza spinta (race conditions, spam di richieste delete/scale), la tolleranza ai guasti nelle chiamate remote e la consistenza eventuale tra il Database locale (H2/PostgreSQL) e l'infrastruttura esterna (Docker Swarm).*
- [ ] **Passo 5.1 (Cancellazione & Operazioni Idempotenti)**:
  - Gestione sicura del fallimento della CLI/API Docker quando una risorsa è già stata eliminata (*No such service* non deve bloccare la pulizia del database).
  - Garantire l'idempotenza degli endpoint (`DELETE /v1/functions/{name}` restituisce esito positivo se la funzione cessa di esistere, indipendentemente da quanti tentativi concorrenti arrivino).
- [ ] **Passo 5.2 (Gestione Race Conditions, Concorrenza & Locking Granulare)**:
  - Protezione dalle race condition su invocazioni contemporanee di `register`, `scale` e `delete` sullo stesso nome di funzione.
  - Implementazione di optimistic locking con `@Version` su `FunctionSpec` in JPA per rilevare conflitti di concorrenza sui metadati.
  - Meccanismo di locking per chiave/funzione (es. `Striped Lock` o lock nominati in memoria) per impedire operazioni concorrenti e distruttive sulla medesima funzione (es. deploy in corso vs delete simultaneo).
- [ ] **Passo 5.3 (Circuit Breaker, Retries & Fallback nel Dispatcher)**:
  - Protezione del Control Plane contro effetti a cascata (*cascading failures*) e saturazione dei thread HTTP quando i container delle funzioni sono lenti o in crash.
  - Introduzione del pattern **Circuit Breaker** su `FunctionDispatcher` (stato *Closed*, *Open*, *Half-Open*) con tempi di apertura e chiusura configurabili.
  - Policy di **Retry con Backoff esponenziale & Jitter** per assorbire anomalie transitorie di rete.
  - Risposta di fallback con codici HTTP precisi (`503 Service Unavailable` o `504 Gateway Timeout`) e header informativi.
- [ ] **Passo 5.4 (Reconciliation Loop / Pattern Riconciliatore & Self-Healing)**:
  - Background task periodico (`@Scheduled` di Spring Boot, ispirato all'architettura a controller di Kubernetes).
  - Algoritmo di riconciliazione a 3 vie tra stato desiderato (H2) e stato effettivo (Docker Swarm):
    1. *Funzione nel catalogo ma assente in Swarm*: ri-creazione automatica del servizio Swarm o transizione a stato `DEGRADED`.
    2. *Servizio in Swarm ma assente nel catalogo*: garbage collection automatica dei container/servizi orfani via `docker service rm`.
    3. *Discrepanza di repliche*: allineamento automatico delle repliche attive rispetto a quelle configurate a catalogo.
  - Esportazione su Prometheus delle metriche del riconciliatore (durata del ciclo, discrepanze rilevate, azioni correttive applicate).
- [ ] **Passo 5.5 (Macchina a Stati della Funzione & Transazioni Compensative)**:
  - Definizione degli stati del ciclo di vita: `PENDING`, `READY`, `DEGRADED`, `SCALING`, `TERMINATING`.
  - Transazioni compensative (Saga minimale): se la creazione del servizio Swarm ha successo ma il readiness check va in timeout irreversibile, scatta il rollback infrastrutturale automatico per non lasciare container zombie.

---

### Fase 6: Architettura DDBMS & Stateful Serverless: Persistenza Distribuita & Ripresa dello Stato al Crash del Nodo
*Obiettivo: Trasformare `mini_nanofaas` da piattaforma serverless puramente stateless a un vero e proprio DDBMS distribuito per Stateful Functions. Garantire che lo stato applicativo di una funzione sia replicato su cluster multi-nodo e possa essere ripreso istantaneamente e senza perdita di dati (Zero Data Loss / RPO=0) quando il nodo fisico o virtuale su cui gira il container subisce un crash improvviso.*
- [ ] **Passo 6.1 (Topologia Swarm Multi-Nodo & Cluster DDBMS Replicato)**:
  - Estensione dell'ambiente locale con Multipass per creare un cluster Docker Swarm multi-nodo (1 Manager + 2 Worker collegati su `nanofaas-net`).
  - Deployment di un engine DDBMS distribuito su Swarm con replica attiva e tolleranza ai partizionamenti (es. cluster Redis Sentinel / Redis Cluster con replica master-replica e persistenza AOF su volumi indipendenti, oppure store distribuito basato su Raft come TiKV/etcd).
  - Bisogna approfondire anche altre soluzioni, come: Xata e StarbaseDB, anche se forse è meglio per rendere le funzioni non più effimere (che ora non centra nulla). Inoltre sono soluzioni non totalmente open-source oppure richiedono iscrizioni a servizi terzi. Sarebbe comunque figo implementare da zero una cosa del genere e renderla open-source.
  - Definizione del modello di consistenza distribuita (Quorum writes, Read-Your-Writes e durability guarantees).
- [ ] **Passo 6.2 (Pattern Stateful Functions & Checkpointing / Write-Ahead Logging)**:
  - Definizione del ciclo di vita dello stato della funzione: astrazione di *Stateful Entity / Virtual Actor* (chiave di partizionamento per-funzione o per-istanza: `fn:<name>:<key>`).
  - Strategia a doppio livello di persistenza:
    1. **Write-Ahead Logging (WAL)**: ogni mutazione dello stato generata durante l'invocazione (`POST :invoke`) viene committata atomicamente sul cluster DDBMS prima di inviare il `200 OK` al client.
    2. **Snapshotting periodico**: compattazione e serializzazione periodica dello stato in-memory per accelerare i tempi di recupero ed evitare replay infiniti dei log.
- [ ] **Passo 6.3 (Fencing Tokens, Lease Distribuiti & Protezione Split-Brain)**:
  - Gestione dei *network partition* e *grey failures* (nodo creduto morto dall'orchestratore, ma ancora attivo e in grado di scrivere).
  - Implementazione del meccanismo di **Fencing Token** (monotonically increasing versioning o lease distribuiti con TTL sul DDBMS).
  - Rifiuto categorico da parte del DDBMS di qualsiasi operazione di scrittura proveniente da istanze "zombie" con token obsoleto, prevenendo divergenze o corruzione silente dello stato.
- [ ] **Passo 6.4 (Node Crash Detection, Swarm Task Rescheduling & State Rehydration)**:
  - Simulazione del crash hardware o arresto improvviso di un worker Swarm (`multipass stop nanofaas-worker-1`).
  - Docker Swarm rileva il mancato heartbeat del nodo e ripianifica (`reschedule`) il container della funzione su un worker superstite.
  - Procedura di **State Rehydration**: al boot sul nuovo nodo, la funzione/sidecar interroga il DDBMS durante il readiness check, ricarica l'ultimo snapshot coerente, riapplica i log del WAL non consolidati e notifica la disponibilità al traffico.
- [ ] **Passo 6.5 (Deduplicazione Invocazioni & Semantica Transazionale Exactly-Once)**:
  - Gestione delle chiamate HTTP in-flight al momento esatto del crash del nodo.
  - Implementazione di **Idempotency Keys** passate dal chiamante o generate dal Dispatcher (`X-Idempotency-Key` salvata con TTL nel DDBMS).
  - Se il client riprova l'invocazione verso la nuova istanza della funzione, il DDBMS rileva la chiave già processata e restituisce la risposta registrata senza rieseguire la logica di business né alterare nuovamente lo stato (semantica *effectively-once*).

---

### Fase 7: Chaos Engineering, Fault Injection & Stress Testing
*Obiettivo: Verificare empiricamente sul campo l'efficacia dei meccanismi di resilienza, la convergenza del Reconciliation Loop, la tenuta del DDBMS e il ripristino istantaneo dello stato al crollo di nodi fisici nello Swarm.*
- [ ] **Passo 7.1 (Chaos Infrastrutturale: Container Kill, Service Termination & Node Eviction Out-of-Band)**:
  - Iniezione di guasti esterni: uccisione violenta di container (`docker kill`), rimozione diretta di servizi Swarm e spegnimento brutale di interi nodi del cluster (`multipass stop nanofaas-worker-1`).
  - Verifica empirica: Swarm ripianifica il servizio sul nodo superstite, il *Reconciliation Loop* corregge le discrepanze, e la funzione ripristina il proprio stato dal DDBMS senza alcuna perdita (*Zero Data Loss verification*).
- [ ] **Passo 7.2 (Chaos di Rete: Latenza, Packet Loss & Partizioni sulla Rete Overlay)**:
  - Simulazione di degrado della rete overlay `nanofaas-net` (tramite `tc` / `iptables` / Toxiproxy o Pumba): iniezione di latenza artificiale (es. 2000ms), perdita di pacchetti e partizioni parziali tra Control Plane, container funzioni e cluster DDBMS.
  - Validazione empirica del Circuit Breaker e dei Fencing Tokens sotto isolamento di rete (verifica assenza di split-brain nel DDBMS).
- [ ] **Passo 7.3 (Concurrency Fuzzing & Stress Test sotto Carico Massivo)**:
  - Test di carico ad alta concorrenza (es. con script Python / k6 / wrk): bombardamento simultaneo di `register`, `scale`, `delete` e `invoke` su centinaia di thread paralleli con mutazioni di stato continue.
  - Verifica della tenuta dei lock e delle transazioni DDBMS: assenza di deadlock nel Control Plane, persistenza integra in H2 e nel DDBMS (nessun dato corrotto o record fantasma).
- [ ] **Passo 7.4 (Crash & Cold Recovery del Control Plane)**:
  - Simulazione di riavvio improvviso del nodo manager o crash del Control Plane (`kill -9`) durante operazioni intensive di scale o registrazione.
  - Bootstrap recovery: all'avvio successivo, il Control Plane deve re-sincronizzare lo stato H2 con Swarm e col DDBMS senza cancellare per errore servizi attivi e sani.
- [ ] **Passo 7.5 (Osservabilità del Chaos: Metriche MTTR, RPO/RTO & Dashboard di Resilienza)**:
  - Monitoraggio in tempo reale durante i test di chaos tramite Prometheus: misurazione di MTTR (*Mean Time to Recovery*), RTO (*Recovery Time Objective*), verifica RPO=0 (*Recovery Point Objective*), tasso di fallimento invocazioni, saturazione pool di connessioni e memoria JVM.
  - Definizione di SLO (Service Level Objectives) minimi di resilienza per la piattaforma.

---

### Fase 8: Ottimizzazione Prestazioni, Cold Start & JVM Next-Gen: AOT Cache (Leyden) e CRaC
*Obiettivo: Esplorare le frontiere dell'avvio istantaneo della JVM per eliminare il problema del Cold Start nelle piattaforme Serverless/FaaS sia lato Control Plane che lato funzioni utente.*
- [ ] **Passo 8.1 (AOT Cache sul Control Plane)**:
  - [ ] Comprensione del modello a due fasi di **Project Leyden / JEP 516** (`-XX:AOTMode=record` durante la "training run" e `-XX:AOTMode=on` a runtime).
  - [ ] Aggiornamento del `Dockerfile` multi-stage del Control Plane per eseguire una corsa di addestramento (context startup + readiness check e shutdown automatico) producendo l'archivio pre-risolto delle classi (`.aot`).
  - [ ] Misurazione del tempo di avvio (`Started MiniNanofaasApplication in X seconds`) e della memoria residente (RSS) prima e dopo l'abilitazione della AOT Cache.
- [ ] **Passo 8.2 (AOT Cache per il Runtime delle Funzioni FaaS)**:
  - [ ] Creazione del template base per funzioni serverless Java con AOT Cache pre-addestrata.
  - [ ] Abbattimento dei tempi di readiness check (`waitForReadiness` in `SwarmProvider`) durante lo scale-up o cold start on-demand.
  - [ ] Verifica del mantenimento delle caratteristiche JVM standard: profilazione con Java Flight Recorder (JFR) e compilazione JIT ad alte prestazioni a regime continuo.
- [ ] **Passo 8.3 (Sperimentazione CRaC - Coordinated Restore at Checkpoint)**:
  - [ ] Studio dell'architettura **CRaC** basata su CRIU (Linux Checkpoint/Restore in Userspace) supportata da Azul Zulu / OpenJDK CRaC.
  - [ ] Gestione del ciclo di vita con l'interfaccia `org.crac.Resource` (`beforeCheckpoint` e `afterRestore`) per chiudere/riaprire connessioni di rete (es. socket Swarm, database, client HTTP).
  - [ ] Creazione di uno snapshot di una funzione pre-riscaldata e ripristino istantaneo sub-100ms.
- [ ] **Passo 8.4 (Analisi Comparativa Prestazioni & Trade-off Architetturali)**:
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

---

## 🔮 Fasi Future & Ricerca Avanzata
*(Nota: la gestione delle Stateful Functions con DDBMS distribuito è stata promossa a Fase 6 attiva del progetto).*
- **Event-Driven Architecture**: integrazione con broker distribuiti (Apache Kafka o NATS JetStream) per abilitare invocazioni asincrone su code di messaggi ed event streams ad altissimo throughput.
- **WebAssembly (Wasm) Runtime**: supporto a moduli Wasm (es. Wasmtime / Wasmer) per esecuzioni serverless multi-linguaggio con cold start sub-millisecondo e footprint di memoria microscopico (<5MB).
- **Federazione Multi-Cluster & Edge Orchestration**: deployment geograficamente distribuito (Edge-to-Cloud) con replica attiva-attiva del catalogo e del DDBMS tra cluster Swarm multipli.
