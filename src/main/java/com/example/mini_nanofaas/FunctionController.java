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

    public FunctionController(FunctionDispatcher dispatcher, FunctionRegistry registry) {
        this.dispatcher = dispatcher;
        this.registry = registry;
    }


    @PostMapping
    public ResponseEntity<FunctionSpec> register(@RequestBody FunctionSpec functionSpec) {
        // @RequestBody dice a Spring di convertire il JSON in ingresso nell'oggetto FunctionSpec

        // proviamo subito a salvare la funzione
        // Il metodo registerFunction restituisce un valore booleano
        // Se la funzione esiste già nel registro, restituiamo un errore 409 (Conflict)
        if (!(registry.registerFunction(functionSpec))) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }
        // Se siamo arrivati qui allora tutto apposto, l'oggetto è stato salvato
        // Restituiamo 201 (Created) con l'oggetto salvato
        return new ResponseEntity<>(functionSpec, HttpStatus.CREATED);
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
}
