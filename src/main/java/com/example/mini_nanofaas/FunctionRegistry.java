package com.example.mini_nanofaas;

import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class FunctionRegistry {

    // Registro in memoria: mappa thread-safe nome -> FunctionSpec
    // ConcurrentHashMap permette a più richieste HTTP concorrenti di leggere/scrivere in sicurezza
    private final Map<String, FunctionSpec> storage = new ConcurrentHashMap<>();

    // Metodi register
    public boolean registerFunction(FunctionSpec function) {
        return storage.putIfAbsent(function.getName(), function) == null;
        /*
        • Se la funzione NON c'era, la inserisce e restituisce null. Quindi
         l'espressione è null == null -> true! (Significa: "registrazione avvenuta con successo!").
        • Se la funzione c'era già, restituisce il valore vecchio (diverso da null).
        Quindi l'espressione è valore == null -> false! (Significa: "fallito, c'era già un duplicato!").
         */
    }

    // Metodo get
    public Optional<FunctionSpec> getFunction(String functionName) {
        return Optional.ofNullable(storage.get(functionName));
    }

    // metodo exists
    public boolean existFunction(String functionName) {
        return storage.containsKey(functionName);
    }

}
