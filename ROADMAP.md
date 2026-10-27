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
- [ ] **Passo 3.3**: Implementazione scaling (`docker service scale`) e rimozione (`docker service rm`).
- [ ] **Passo 3.4 (Sessione Refactoring IntelliJ #2 - Architettura, Design Pattern & Astrazioni)**:
  - *Obiettivo*: Riorganizzare l'architettura man mano che cresce la complessità, introducendo pattern architetturali puliti grazie ai refactoring avanzati di IntelliJ IDEA.
  - *Funzionalità e shortcut IntelliJ*:
    - **Extract Interface / Superclass**: estrarre un'interfaccia da `SwarmProvider` (es. `ContainerOrchestrator` o `FunctionProvider`) per preparare il codice all'eventuale supporto di altri backend o mock di test.
    - **Move Members / Move Class** (`F6`): spostamento pulito di classi ed entità verso package dedicati (es. `controller`, `service`, `provider`, `model`) lasciando che IntelliJ aggiorni tutti gli import e le dipendenze.
    - **Change Signature** (`Ctrl + F6`): modificare firme di metodi (aggiunta/rimozione parametri, eccezioni, ritorni) con aggiornamento automatico di tutti i punti di chiamata.
    - **Refactor This** (`Ctrl + Alt + Shift + T`): menu contestuale rapido di refactoring per valutare e applicare la trasformazione adatta in qualsiasi punto del codice.
    - **Safe Delete** (`Alt + Delete`): rimozione sicura di codice morto o residui di vecchie implementazioni con controllo preventivo dei riferimenti.

---

### Fase 4: Monitoring, Funzione Demo & Developer Tooling
- [ ] **Passo 4.1**: Creazione immagine funzione demo `test-greet` (con endpoint `/health` e `/invoke`).
- [ ] **Passo 4.2**: Deployment container **Prometheus** nella VM Multipass su `nanofaas-net`:
  - Configurazione dello scraping continuo su `http://control-plane:8080/actuator/prometheus`.
- [ ] **Passo 4.3**: Test end-to-end: registrazione da API -> creazione servizio Swarm -> invocazione -> verifica metriche su Prometheus.
- [ ] **Passo 4.4**: CLI Python per lo sviluppatore/operatore:
  - Comandi rapidi da host per impacchettare nuove funzioni, deployarle su `mini_nanofaas` e controllarne i log.

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

---

## 🔮 Fase Futura: Stateful Functions via Watchdog (Rust) & Redis
- Copiare il codice del **Watchdog in Rust** dal progetto originale `nanofaas`.
- Modificare il Watchdog per fungere da sidecar State API ed estendere il runtime con la gestione dello stato interno delle funzioni tra le varie invocazioni.
- Integrazione con **Redis** deployato nello Swarm (`nanofaas-net`) per offrire endpoint di state storage (es. `GET /state/{key}`, `POST /state/{key}`) al codice utente tramite il Watchdog.
