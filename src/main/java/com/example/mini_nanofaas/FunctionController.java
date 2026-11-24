package com.example.mini_nanofaas;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;



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
    // Esecutore in-process per funzioni con sourceCode: quando usato, bypassa FunctionDispatcher e Docker.
    private final InlineJavaExecutor inlineJavaExecutor;

    // Richiede esattamente un bean ProviderFunzione (LocalProvider di default oppure SwarmProvider con
    // nanofaas.provider=swarm): altri valori di nanofaas.provider causano NoSuchBeanDefinitionException all'avvio.
    public FunctionController(
            FunctionDispatcher dispatcher,
            FunctionRegistry registry,
            ProviderFunzione provider,
            InlineJavaExecutor inlineJavaExecutor
    ) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.functionProvider = provider;
        this.inlineJavaExecutor = inlineJavaExecutor;
    }

    /**
     * Restituisce l'elenco di tutte le funzioni registrate nel sistema.
     * <p>
     * Espone anche le funzioni scalate a 0 repliche e include integralmente il campo
     * {@code sourceCode} senza paginazione né ordinamento (consumato dalla Web GUI).
     *
     * @return lista delle {@link FunctionSpec} presenti nel registro
     */
    @GetMapping
    public ResponseEntity<List<FunctionSpec>> list() {
        return ResponseEntity.ok(registry.getAllFunctions());
    }


    @PostMapping
    public ResponseEntity<FunctionSpec> register(@RequestBody FunctionSpec functionSpec) {
        // @RequestBody indica a Spring di convertire il JSON della richiesta HTTP nell'oggetto FunctionSpec

        // PASSO 1: Validazione delle invarianti di dominio (Rich Domain Model / Information Expert)
        // Interroghiamo direttamente FunctionSpec per verificare se la configurazione è valida
        if (!functionSpec.hasValidConfiguration()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Normalizzazione eseguita dopo hasValidConfiguration() e prima di deploy() per evitare imageDigest vuoto.
        // Attenzione: con nanofaas.provider=swarm, SwarmProvider non controlla hasSourceCode() e tenterà
        // "docker service create ... inline-java", fallendo il deploy dell'immagine fittizia.
        if ((functionSpec.getImageDigest() == null || functionSpec.getImageDigest().isBlank())
                && functionSpec.hasSourceCode()) {
            functionSpec.setImageDigest("inline-java");
        }

        // PASSO 2: Verifica di conflitti nel registro H2
        // Se una funzione con questo nome è già registrata, restituiamo 409 (Conflict)
        if (registry.existsFunction(functionSpec.getName())) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        // PASSO 3: Creazione e attesa di avvio (Docker Swarm o provider locale)
        try {
            boolean isServiceSuccessfullyDeployed = functionProvider.deploy(functionSpec);
            if (!isServiceSuccessfullyDeployed) {
                // Se il deploy infrastrutturale fallisce, non persistiamo nulla su database per evitare inconsistenze
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IllegalArgumentException e) {
            // Intercetta errori di compilazione javac da LocalProvider.validateCompilation() (400),
            // ma NON verifica la presenza di handle(String) (rimandata a invoke()). Una JRE priva di javac
            // lancia IllegalStateException che, non catturata qui, risale come 500 senza salvare su H2.
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // PASSO 4: Persistenza a deploy completato (Pattern "Check prima di Salvare")
        // Il boolean restituito è ignorato e i passi 2-4 non sono transazionali: POST concorrenti sullo stesso
        // name possono superare entrambe existsFunction/deploy e restituire 201 anche se il salvataggio ritorna false.
        registry.registerOrSaveFunction(functionSpec);
        return new ResponseEntity<>(functionSpec, HttpStatus.CREATED);
    }

    // endpoint di invocazione della funzione (su nanofaas vero ci si collega ad altri moduli, qua semplifichiamo le cose)
    @PostMapping("/{name}:invoke")
    public ResponseEntity<String> invoke(@PathVariable("name") String name, @RequestBody(required = false) String payload) {
        // Carica subito FunctionSpec (inclusi sourceCode e replicas) evitando una seconda query,
        // senza però lock transazionale rispetto a DELETE o /scale concorrenti.
        var optionalSpec = registry.getFunction(name);
        if (optionalSpec.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        FunctionSpec spec = optionalSpec.get();
        // Priorità assoluta al percorso inline: se sourceCode è valorizzato, bypassa sempre Docker/Swarm
        // anche in presenza di un imageDigest reale o di nanofaas.provider=swarm.
        if (spec.hasSourceCode()) {
            // Per funzioni inline replicas vale solo come flag attivo/inattivo (>0 vs 0), non come pool di thread
            // (execute() ricompila ad ogni chiamata su un SingleThreadExecutor dedicato).
            if (spec.getReplicas() == 0) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Funzione scalata a 0 repliche: aumentale ad almeno 1 per invocarla.");
            }
            try {
                String result = inlineJavaExecutor.execute(spec.getSourceCode(), payload, spec.getTimeoutSeconds());
                return ResponseEntity.ok(result);
            } catch (InlineJavaExecutor.FunctionTimeoutException e) {
                // I due esiti booleani sono ignorati. Un 504 può quindi arrivare anche se il provider
                // ha rifiutato lo scaling o se un DELETE concorrente ha già rimosso la riga da H2.
                functionProvider.scale(name, 0);
                registry.updateReplicas(name, 0);
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(e.getMessage());
            } catch (IllegalArgumentException e) {
                // Include errori di firma scoperti solo a runtime (es. assenza di handle(String)), non bloccati al deploy.
                return ResponseEntity.badRequest().body(e.getMessage());
            } catch (Exception e) {
                // Errori di esecuzione della funzione utente, istanziazione o JRE priva di javac.
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
            }
        }

        // Asimmetria rispetto al ramo inline: per funzioni Docker/Swarm non controlla replicas==0 (niente 503),
        // non applica timeoutSeconds/scale-to-0 e lascia risalire eventuali RestClientException come 500.
        // qui chiamiamo il FunctionDispatcher che contatterà il container Docker della funzione e restituirà l'output
        String response = dispatcher.dispatch(name, payload);
        return ResponseEntity.ok(response);
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
