# Roadmap 2nd Assignment — GitLab CI/CD (`mini_nanofaas`)

Questa roadmap è pensata come percorso guidato passo passo per completare il **2nd Assignment** su `mini_nanofaas` in un **team di 4 persone**.

> **Regola per l'assistente**: **NON scrivere codice al posto dell'utente.** In ogni fase:
> 1. Fai prima una **breve introduzione teorica** che spieghi le scelte tecniche (es. cos'è Gradle vs Maven, perché configuriamo la cache in un certo modo, ecc.).
> 2. Guida poi l'utente passo passo spiegando *cosa* fare e *dove* farlo in IntelliJ IDEA, lasciando che sia l'utente a scrivere e testare il codice.

---

## Obiettivi e Vincoli dell'Assignment

- **6 Stage obbligatori**: `build`, `verify`, `test`, `package`, `release`, `docs` (+ `deploy` opzionale).
- **Vincolo `verify`**: analisi **statica** e **dinamica** eseguite **in parallelo**.
- **Vincolo team (4+ persone)**: almeno **2 pipeline su 2 branch git diversi** (es. `develop` e `master`). Ogni branch può eseguire un sottoinsieme di stage, ma insieme devono coprire tutti e 6 gli stage obbligatori.
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
  - **Branch `master`** (rilascio e documentazione): `build` → `verify` → `test` → `package` → `release` → `docs` *(+ `deploy` opzionale)*.
- [x] Verificare che il progetto compili e giri in locale: dalla finestra **Gradle** di IntelliJ (pannello laterale destro) eseguire **Tasks → build → build** (equivalente a `./gradlew build`).

### Fase 1 — Stage `build` (Compilazione e Dipendenze)
- **Cosa impariamo**: come GitLab Runner scarica le dipendenze Gradle e compila il progetto Java usando la cache per velocizzare i job.
- [x] Creare/modificare `.gitlab-ci.yml` in IntelliJ (sfruttando autocompletamento e validazione YAML integrata) configurando il job `build` con un'immagine Docker Java/Gradle adatta.
- [x] Impostare `cache` per `.gradle/` e `artifacts` per passare i file compilati agli stage successivi.

### Fase 2 — Stage `verify` (Analisi Statica e Dinamica in Parallelo)
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- **Cosa impariamo**: differenza tra analisi statica (qualità/bug senza eseguire il codice) e analisi dinamica (comportamento/coverage a runtime), e come farle girare **in parallelo** su GitLab CI.
- **Analisi statica (in locale + CI)**:
  - [ ] Introduzione teorica: cos'è l'analisi statica (PMD / Checkstyle / SpotBugs) e perché non richiede l'esecuzione del codice.
  - [ ] Aggiungere il plugin scelto (es. `pmd`) in `build.gradle` e fare **Reload All Gradle Projects** in IntelliJ.
  - [ ] Configurare il blocco del plugin in `build.gradle` (es. output su console e `ignoreFailures` per non bloccare subito sui warning).
  - [ ] Testare il task in locale dal pannello **Gradle → Tasks → verification** (es. `pmdMain`) e aprire il report HTML in `build/reports/`.
  - [ ] Aggiungere lo stage `verify` in `stages:` di `.gitlab-ci.yml`.
  - [ ] Scrivere il job `verify_static_job` in `.gitlab-ci.yml` (`stage: verify`, `needs: ["build_job"]`, `artifacts` per i report, `rules` per `develop` e `master`).
- **Analisi dinamica (in locale + CI)**:
  - [ ] Introduzione teorica: cos'è l'analisi dinamica (es. JaCoCo code coverage) e perché richiede l'esecuzione dei test.
  - [ ] Aggiungere il plugin `jacoco` in `build.gradle` e fare **Reload All Gradle Projects**.
  - [ ] Configurare `jacocoTestReport` (report XML/HTML) e collegarlo a `test` (`finalizedBy`) in `build.gradle`.
  - [ ] Testare in locale da **Gradle → Tasks → verification → jacocoTestReport** (oppure **Run with Coverage** in IntelliJ) e aprire il report HTML.
  - [ ] Scrivere il job `verify_dynamic_job` in `.gitlab-ci.yml` (`stage: verify`, `needs: ["build_job"]`, `artifacts` per `build/reports/jacoco/`, `rules` per `develop` e `master`).
- **Verifica su GitLab**:
  - [ ] Fare **Commit & Push** su `develop` da IntelliJ.
  - [ ] Aprire **Build → Pipelines** su GitLab e verificare graficamente che `verify_static_job` e `verify_dynamic_job` partano **in parallelo** nello stage `verify`.

### Fase 3 — Stage `test` (Unit / Integration Test)
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- **Cosa impariamo**: automatizzare l'esecuzione dei test in CI e integrare i risultati JUnit direttamente nell'interfaccia di GitLab.
- [ ] Introduzione teorica: differenza tra test unitari/di integrazione, ruolo di JUnit Platform in Gradle e come GitLab legge i report XML.
- [ ] Eseguire e verificare tutti i test in locale in IntelliJ (tasto destro su `src/test` → **Run 'All Tests'** o **Gradle → Tasks → verification → test**).
- [ ] Controllare dove Gradle salva i report XML (`build/test-results/test/TEST-*.xml`).
- [ ] Aggiungere `test` in `stages:` di `.gitlab-ci.yml`.
- [ ] Scrivere il job `test_job` in `.gitlab-ci.yml` (`./gradlew test --no-daemon`) con `needs` dagli step precedenti.
- [ ] Configurare `artifacts:reports:junit` su `build/test-results/test/TEST-*.xml` (con `when: always`) per vedere i test nella tab **Tests** di GitLab.
- [ ] Fare **Commit & Push** su `develop` e verificare che la tab **Tests** della pipeline su GitLab mostri tutti i test passati.

### Fase 4 — Stage `package` (Creazione dell'Artefatto)
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- **Cosa impariamo**: generare il pacchetto eseguibile (`.jar` Spring Boot) pronto per la distribuzione, **solo** sul branch di rilascio (`master`).
- [ ] Introduzione teorica: differenza tra `jar` standard e `bootJar` (fat/uber JAR di Spring Boot) e perché `package` gira solo su `master`.
- [ ] Testare in locale da IntelliJ (**Gradle → Tasks → build → bootJar**) e verificare il `.jar` generato dentro `build/libs/`.
- [ ] Aggiungere `package` in `stages:` di `.gitlab-ci.yml`.
- [ ] Scrivere il job `package_job` in `.gitlab-ci.yml` (`./gradlew bootJar --no-daemon`).
- [ ] Salvare `build/libs/*.jar` in `artifacts:paths` per passarlo allo stage `release`.
- [ ] Impostare `rules:` in modo che `package_job` giri **solo** su `master` (`$CI_COMMIT_BRANCH == "master"`).

### Fase 5 — Stage `release` (Docker Image & Container Registry)
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- **Cosa impariamo**: costruire un'immagine Docker usando il `Dockerfile` del progetto e pubblicarla sul **GitLab Container Registry**.
- [ ] Introduzione teorica: cos'è un Container Registry, come funziona Docker-in-Docker (`dind`) vs Kaniko/Buildah su GitLab CI, e cosa sono le variabili automatiche `CI_REGISTRY`, `CI_REGISTRY_USER`, `CI_JOB_TOKEN`.
- [ ] Ispezionare il `Dockerfile` esistente in IntelliJ per capire quali file si aspetta (es. il `.jar` prodotto nello stage `package`).
- [ ] Aggiungere `release` in `stages:` di `.gitlab-ci.yml`.
- [ ] Scrivere il job `release_job` in `.gitlab-ci.yml` (con `rules` solo su `master` e `needs: ["package_job"]`).
- [ ] Configurare `docker login` verso `$CI_REGISTRY` usando `$CI_JOB_TOKEN`.
- [ ] Configurare `docker build` e `docker push` dell'immagine taggata con `$CI_COMMIT_SHORT_SHA` e `latest`.
- [ ] Dopo il merge su `master`, verificare su GitLab in **Deploy → Container Registry** che l'immagine sia stata pubblicata.

### Fase 6 — Stage `docs` (Generazione e Pubblicazione Documentazione)
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- **Cosa impariamo**: generare documentazione automatica dal codice (Javadoc) e pubblicarla gratis su **GitLab Pages**.
- [ ] Introduzione teorica: cos'è Javadoc, come funziona **GitLab Pages** e perché il job deve chiamarsi obbligatoriamente `pages` con output nella cartella `public/`.
- [ ] Testare in locale in IntelliJ (**Gradle → Tasks → documentation → javadoc**) e controllare l'HTML generato in `build/docs/javadoc/`.
- [ ] Aggiungere `docs` in `stages:` di `.gitlab-ci.yml`.
- [ ] Scrivere il job `pages` in `.gitlab-ci.yml` (con `rules` solo su `master`).
- [ ] Nello `script`, eseguire `./gradlew javadoc --no-daemon` e copiare `build/docs/javadoc/*` dentro `public/`.
- [ ] Configurare `artifacts:paths: [public]`.
- [ ] Dopo il merge su `master`, verificare su GitLab in **Deploy → Pages** che il sito della documentazione sia raggiungibile.

### Fase 7 — Stage `deploy` *(Opzionale)*
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- [ ] Introduzione teorica: differenza tra Continuous Delivery (`release`) e Continuous Deployment (`deploy`) e gestione sicura di chiavi SSH nelle GitLab CI/CD Variables.
- [ ] *(Opzionale)* Configurare il job `deploy_job` su `master` per collegarsi via SSH a una VM ed eseguire `docker pull` + `docker run` dell'immagine pubblicata nello stage `release`.

### Fase 8 — Rifinitura, Commenti e Consegna
> **Nota:** l'assistente fa prima un'introduzione teorica e poi guida passo passo; **l'utente scrive il codice**.
- [ ] Fare **Merge** di `develop` su `master` (tramite **Merge Request** su GitLab) e verificare che la pipeline completa (`build` → `verify` → `test` → `package` → `release` → `docs`) su `master` diventi tutta verde.
- [ ] Rileggere `.gitlab-ci.yml` e verificare che ogni stage e job abbia **commenti chiari** in italiano/inglese che spiegano i singoli step (requisito obbligatorio dell'assignment).
- [ ] Aggiornare `README.md` aggiungendo:
  - Breve descrizione di ogni stage della pipeline.
  - Lista completa dei 4 membri del gruppo.
  - URL della repository GitLab (`2026_assignment2_{your_project_name}`).
- [ ] Esportare `README.md` in **PDF** (da IntelliJ o da browser/VS Code/Pandoc) e controllare che formattazione e link siano leggibili.
- [ ] Caricare il PDF su **eLearning** (un solo membro del gruppo per ciascuna delle 2 consegne previste).
