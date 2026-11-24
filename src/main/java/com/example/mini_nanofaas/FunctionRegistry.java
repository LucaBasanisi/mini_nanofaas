package com.example.mini_nanofaas;

import org.springframework.stereotype.Repository;
import java.util.List;
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

    /**
     * Restituisce tutte le funzioni attualmente registrate nel catalogo H2.
     * <p>
     * La GUI filtra le funzioni inattive dopo la ricezione e usa il sorgente per la modale.
     * Anche a tabella vuota, il costo della risposta dipende dal numero di righe in H2
     * e dalla dimensione dei sorgenti. L'ordine delle righe è indeterminato.
     *
     * @return lista di tutte le {@link FunctionSpec} salvate
     */
    public List<FunctionSpec> getAllFunctions() {
        return repository.findAll();
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
