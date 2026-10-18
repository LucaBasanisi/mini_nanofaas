package com.example.mini_nanofaas;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/v1/functions")
public class FunctionController {
    // @RestController indica a Spring che questa classe riceve richieste HTTP e restituisce dati JSON
    // @RequestMapping definisce il prefisso dell'URL per tutti i metodi di questa classe

    // Registro in memoria: mappa thread-safe nome -> FunctionSpec
    // ConcurrentHashMap permette a più richieste HTTP concorrenti di leggere/scrivere in sicurezza
    private final Map<String, FunctionSpec> registry = new ConcurrentHashMap<>();

    @PostMapping
    public ResponseEntity<FunctionSpec> register(@RequestBody FunctionSpec functionSpec) {
        // @RequestBody dice a Spring di convertire il JSON in ingresso nell'oggetto FunctionSpec

        // Se la funzione esiste già nel registro, restituiamo un errore 409 (Conflict)
        if (registry.containsKey(functionSpec.getName())) {
            return new ResponseEntity<>(registry.get(functionSpec.getName()), HttpStatus.CONFLICT);
        }

        // Altrimenti salviamo la funzione nel registro
        registry.put(functionSpec.getName(), functionSpec);

        // Restituiamo 201 (Created) con l'oggetto salvato
        return new ResponseEntity<>(functionSpec, HttpStatus.CREATED);
    }
}
