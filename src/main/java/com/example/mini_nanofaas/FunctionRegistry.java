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

    /**
     * Aggiorna lo stato delle repliche nel database H2 dopo l'avvenuto scaling su Docker Swarm.
     * Applica la tecnica Move Method delegando a {@link FunctionSpec#updateReplicaCount(int)}
     * la mutazione protetta del valore invece di manipolare campi grezzi dall'esterno.
     *
     * @param functionName Nome identificativo univoco della funzione da aggiornare
     * @param newNumberOfReplicas Nuovo numero di container desiderato
     * @return true se la funzione è presente nel registro ed è stata aggiornata, false altrimenti
     */
    public boolean updateReplicas(String functionName, int newNumberOfReplicas) {
        Optional<FunctionSpec> optionalFunctionSpecification = repository.findById(functionName);
        if (optionalFunctionSpecification.isPresent()) {
            FunctionSpec functionSpecification = optionalFunctionSpecification.get();
            // Deleghiamo a FunctionSpec la mutazione protetta delle repliche (Rich Domain Model)
            functionSpecification.updateReplicaCount(newNumberOfReplicas);
            repository.save(functionSpecification);
            return true;
        }
        return false;
    }
}
