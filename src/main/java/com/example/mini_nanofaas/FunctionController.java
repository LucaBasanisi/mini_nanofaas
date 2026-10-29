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
