package com.example.mini_nanofaas;

import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class FunctionRegistry {

    private final FunctionRepository repository;
    public FunctionRegistry(FunctionRepository repository) {
        this.repository = repository;
    }

    // Salva o Aggiorna la funzione
    public boolean registerOrSaveFunction(FunctionSpec function) {
        if (repository.existsById(function.getName())) {
            return false;
        }
        repository.save(function);
        return true;
    }

    // Metodo get: cerca per nome (chiave primaria)
    public Optional<FunctionSpec> getFunction(String functionName) {
        return repository.findById(functionName);
    }

    // Metodo exists: verifica presenza per chiave primaria
    public boolean existsFunction(String functionName) {
        return repository.existsById(functionName);
    }

    public void deleteFunction(String functionName) {
        repository.deleteById(functionName);
    }

    // aggiorniamo il database dopo aver scalato le repliche
    // Non vogliamo un numero di repliche diverso
    public boolean updateReplicas(String functionName, int replicas) {
        Optional<FunctionSpec> funzione = repository.findById(functionName);
        if (funzione.isPresent()) {
            FunctionSpec spec = funzione.get();
            spec.setReplicas(replicas);
            repository.save(spec);
            return true;
        }
        return false;
    }
}
