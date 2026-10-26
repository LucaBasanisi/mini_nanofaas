package com.example.mini_nanofaas;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FunctionRepository extends JpaRepository<FunctionSpec, String> {

    // T è il TIPO dell'oggetto nel database, nel nostro caso sarà FunctionSpec
    // ID è la Primary Key
    // A runtime viene generata una classe proxy che implementa ricerca, salvataggio, etc... per il database

}
