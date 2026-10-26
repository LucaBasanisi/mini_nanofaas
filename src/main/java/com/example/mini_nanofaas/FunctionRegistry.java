package com.example.mini_nanofaas;

import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class FunctionRegistry {

    private final FunctionRepository repository;
    public FunctionRegistry(FunctionRepository repository) {
        this.repository = repository;
    }

    // Metodo register: salva solo se non esiste già
    public boolean registerFunction(FunctionSpec function) {
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
}
