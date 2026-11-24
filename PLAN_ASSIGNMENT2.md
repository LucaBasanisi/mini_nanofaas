# Piano di Lavoro – Aggiunta GUI a `mini_nanofaas`

Questo documento descrive il piano per dotare **mini_nanofaas** di una **Web GUI** (servita direttamente da Spring Boot), così da soddisfare il requisito applicativo del 2nd Assignment lasciando al team la gestione completa di Gradle, pipeline CI/CD GitLab e documentazione.

---

## 1. Backend: Endpoint di lettura (`GET /v1/functions`)

1. **`FunctionRegistry`**: aggiungere il metodo `getAllFunctions()` che restituisce `List<FunctionSpec>` tramite `repository.findAll()`.
2. **`FunctionController`**: aggiungere l'endpoint `GET /v1/functions` per esporre la lista delle funzioni registrate in formato JSON.

---

## 2. Frontend: Web GUI (`src/main/resources/static/index.html`)

Realizzare una Single-Page UI (HTML + CSS + JavaScript `fetch` nativo) servita automaticamente da Spring Boot su `http://localhost:8080/`:

- **Create (`POST /v1/functions`)**: form per registrare una nuova funzione (`name`, `imageDigest`, `replicas`, `timeoutSeconds`).
- **Read (`GET /v1/functions`)**: tabella delle funzioni registrate con immagine, repliche, timeout e pulsanti di azione rapida.
- **Update / Scale (`POST /v1/functions/{name}/scale?replicas=...`)**: aggiornamento del numero di repliche direttamente dalla riga della funzione.
- **Delete (`DELETE /v1/functions/{name}`)**: rimozione della funzione dal registro e da Swarm.
- **Invoke (`POST /v1/functions/{name}:invoke`)**: pannello per inviare un payload testuale alla funzione selezionata e visualizzare la risposta.

---

## 3. Test (Unit + Integrazione E2E HTTP)

- Aggiungere `FunctionControllerIntegrationTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` con H2 in memoria e `ProviderFunzione` / `FunctionDispatcher` mockati per non dipendere da Docker Swarm):
  - verificare che `GET /` serva `index.html` (`200 OK`);
  - verificare il ciclo CRUD completo esposto alla GUI: `POST /v1/functions` → `GET /v1/functions` → `POST /v1/functions/{name}/scale` → `POST /v1/functions/{name}:invoke` → `DELETE /v1/functions/{name}`.
