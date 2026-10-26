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
3. **Swarm Provider**: Gestore del ciclo di vita dei container su cluster Docker Swarm (`create`, `scale`, `remove`, health-check).
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

## 💻 Come Avviare

### 1. Avvio in Locale (Sviluppo Spring Boot)
```bash
# Compilazione ed esecuzione dei test
./gradlew test

# Avvio del Control Plane
./gradlew bootRun
```

### 2. Avvio su Cluster Swarm (VM Multipass)
Il Control Plane viene pacchettizzato e pubblicato automaticamente come immagine OCI su GitHub Container Registry:

```bash
docker service create \
  --name control-plane \
  --network nanofaas-net \
  --publish published=8080,target=8080 \
  --mount type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock \
  ghcr.io/lucabasanisi/mini_nanofaas-controlplane:latest
```
