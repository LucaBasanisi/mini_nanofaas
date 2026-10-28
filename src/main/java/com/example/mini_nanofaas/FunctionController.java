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
    private final SwarmProvider swarmProvider;

    public FunctionController(FunctionDispatcher dispatcher, FunctionRegistry registry,  SwarmProvider swarmProvider) {
        this.dispatcher = dispatcher;
        this.registry = registry;
        this.swarmProvider = swarmProvider;
    }


    @PostMapping
    public ResponseEntity<FunctionSpec> register(@RequestBody FunctionSpec functionSpec) {
        // @RequestBody dice a Spring di convertire il JSON in ingresso nell'oggetto FunctionSpec

        // proviamo subito a salvare la funzione
        // Il metodo registerFunction restituisce un valore booleano
        // Se la funzione esiste già nel registro, restituiamo un errore 409 (Conflict)
        if (registry.existsFunction(functionSpec.getName())) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        // Creiamo il container su docker, ci vuole un pò perché sta facendo un ciclo
        boolean container_docker = swarmProvider.deploy(functionSpec);

        if (!container_docker) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            // Se non riusciamo a creare il container allora c'è un problema
            // Ovviamente prima cancelliamo la funzione salvato nel registry per
        } else {
            registry.registerOrSaveFunction(functionSpec);
            return new ResponseEntity<>(functionSpec, HttpStatus.CREATED);
        }
        // Se siamo arrivati qui allora tutto apposto, l'oggetto è stato salvato
        // Restituiamo 201 (Created) con l'oggetto salvato
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
            if (!swarmProvider.remove(name)) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                // Cancelliamo la funzione da H2
                registry.deleteFunction(name);
                return new ResponseEntity<>(HttpStatus.OK);
            }
        } else return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }


    // Un esempio di chiamata sarà .../{name}/scale?replicas=3
    @PostMapping("/{name}/scale")
    public ResponseEntity<String> update(@PathVariable("name") String name, @RequestParam int replicas) {

        // Funzione esiste nel registry?
        if (registry.existsFunction(name)) {
            // Aggiorniamo swarm
            if (!swarmProvider.scale(name, replicas)) {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                // Aggiorniamo H2
                if(registry.updateReplicas(name, replicas)) {
                    return new ResponseEntity<>(HttpStatus.OK);
                } else return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

}
