# Roadmap 2nd Assignment — GitLab CI/CD (`mini_nanofaas`)

Questa roadmap è pensata come percorso guidato passo passo per completare il **2nd Assignment** su `mini_nanofaas` in un **team di 4 persone**.

---

## Obiettivi e Vincoli dell'Assignment

- **6 Stage obbligatori**: `build`, `verify`, `test`, `package`, `release`, `docs` (+ `deploy` opzionale).
- **Vincolo `verify`**: analisi **statica** e **dinamica** eseguite **in parallelo**.
- **Vincolo team (4+ persone)**: almeno **2 pipeline su 2 branch git diversi** (es. `develop` e `main`). Ogni branch può eseguire un sottoinsieme di stage, ma insieme devono coprire tutti e 6 gli stage obbligatori.
- **Documentazione**:
  - `.gitlab-ci.yml` deve contenere **commenti** che spiegano i singoli step.
  - `README.md` deve includere: breve descrizione di ogni stage, lista dei membri del gruppo, URL della repository GitLab (`2026_assignment2_{your_project_name}`).
  - Esportazione del `README.md` in **PDF** per la consegna su eLearning (prima consegna intermedia + seconda consegna finale).
- **Accesso docenti**: aggiungere `sw_dev_process_course` come **Developer** sulla repo GitLab.

---

## Piano di Lavoro Passo Passo

### Fase 0 — Setup Repository e Branching Strategy
- [x] Creare la repository su GitLab: `2026_assignment2_{your_project_name}` e collegarla da IntelliJ (**Git → Manage Remotes... → Push**).
- [x] Aggiungere l'utente `sw_dev_process_course` con ruolo **Developer** (da GitLab: **Manage → Members**).
- [x] Definire la strategia dei **2 branch** per il team da 4 persone (in IntelliJ dal widget **Git Branch** in basso a destra o **Git → Branches... → New Branch**):
  - **Branch `develop`** (integrazione continua): `build` → `verify` (statica + dinamica in parallelo) → `test`.
  - **Branch `main`** (rilascio e documentazione): `build` → `verify` → `test` → `package` → `release` → `docs` *(+ `deploy` opzionale)*.
- [x] Verificare che il progetto compili e giri in locale: dalla finestra **Gradle** di IntelliJ (pannello laterale destro) eseguire **Tasks → build → build** (equivalente a `./gradlew build`).

### Fase 1 — Stage `build` (Compilazione e Dipendenze)
- **Cosa impariamo**: come GitLab Runner scarica le dipendenze Gradle e compila il progetto Java usando la cache per velocizzare i job.
- [ ] Creare/modificare `.gitlab-ci.yml` in IntelliJ (sfruttando autocompletamento e validazione YAML integrata) configurando il job `build` con un'immagine Docker Java/Gradle adatta.
- [ ] Impostare `cache` per `.gradle/` e `artifacts` per passare i file compilati agli stage successivi.

### Fase 2 — Stage `verify` (Analisi Statica e Dinamica in Parallelo)
- **Cosa impariamo**: differenza tra analisi statica (qualità/bug senza eseguire il codice) e analisi dinamica (comportamento a runtime), e come eseguirle **in parallelo** in GitLab CI.
- [ ] **Analisi statica**: integrare in `build.gradle` tool come **Checkstyle** e/o **SpotBugs** (o PMD) e creare il job dedicato. In IntelliJ puoi testare i task dalla finestra **Gradle → Tasks → verification** (es. `checkstyleMain`, `spotbugsMain`) e visualizzare i report HTML generati in `build/reports/`.
- [ ] **Analisi dinamica**: configurare un job parallelo nello stage `verify` (es. **JaCoCo** per code coverage / profiling o esecuzione con analisi runtime/memory). In IntelliJ puoi verificare la coverage anche in locale con **Run with Coverage**.
- [ ] Verificare nella UI di GitLab (o dal pannello **GitLab** / Merge Requests di IntelliJ) che i due job partano in parallelo nello stesso stage.

### Fase 3 — Stage `test` (Unit / Integration Test)
- **Cosa impariamo**: automatizzare l'esecuzione dei test e pubblicare i report JUnit direttamente su GitLab.
- [ ] Configurare il job `test` (`./gradlew test`). In IntelliJ puoi eseguire e debuggare i test direttamente con il tasto destro sulla cartella `src/test` → **Run 'All Tests'** o dalla finestra **Gradle → Tasks → verification → test**.
- [ ] Esporre i report XML di JUnit tramite `artifacts:reports:junit` per visualizzare i risultati nelle Merge Request e nelle pipeline.

### Fase 4 — Stage `package` (Creazione dell'Artefatto)
- **Cosa impariamo**: generare il pacchetto eseguibile (`.jar`) pronto per la distribuzione.
- [ ] Configurare `./gradlew bootJar` / `jar` (o `assemble`) per produrre l'artefatto; testabile in locale da **Gradle → Tasks → build → bootJar** / **assemble**.
- [ ] Salvare il `.jar` come `artifacts` per lo stage `release`.

### Fase 5 — Stage `release` (Docker Image & Container Registry)
- **Cosa impariamo**: costruire un'immagine Docker a partire dal `Dockerfile` esistente e pubblicarla sul **GitLab Container Registry**.
- [ ] Configurare Docker-in-Docker (`dind`) o Kaniko/Buildah in `.gitlab-ci.yml`.
- [ ] Effettuare il login al `CI_REGISTRY` usando `CI_REGISTRY_USER` e `CI_JOB_TOKEN`.
- [ ] Fare `docker build` e `docker push` dell'immagine taggata (es. con `$CI_COMMIT_SHORT_SHA` e `latest`).

### Fase 6 — Stage `docs` (Generazione e Pubblicazione Documentazione)
- **Cosa impariamo**: generare documentazione automatica (Javadoc e/o script `generate_docs.py` / MkDocs) e pubblicarla su **GitLab Pages**.
- [ ] Generare la documentazione (es. `./gradlew javadoc` e/o output di `generate_docs.py`).
- [ ] Configurare il job `pages` (nome obbligatorio su GitLab) con `artifacts:paths: [public]` per pubblicare su GitLab Pages.

### Fase 7 — Stage `deploy` *(Opzionale)*
- [ ] Eventuale deploy automatico su una VM via SSH eseguendo il container Docker pubblicato nello stage `release`.

### Fase 8 — Rifinitura, Commenti e Consegna
- [ ] Aggiungere commenti chiari in `.gitlab-ci.yml` per ogni job e stage.
- [ ] Aggiornare `README.md` con:
  - Descrizione breve di ogni stage
  - Lista dei 4 membri del gruppo
  - URL della repository GitLab
- [ ] Esportare `README.md` in PDF e caricarlo su eLearning.
