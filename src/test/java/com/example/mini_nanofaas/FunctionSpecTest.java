package com.example.mini_nanofaas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Test di Unità per FunctionSpec (Rich Domain Model)")
class FunctionSpecTest {

    @Test
    @DisplayName("Una specifica valida deve superare la validazione di configurazione")
    void shouldValidateCorrectSpecification() {
        FunctionSpec spec = new FunctionSpec("saluta", "alpine:latest", 1, 30);
        assertTrue(spec.hasValidConfiguration(), "Una specifica con dati completi deve risultare valida.");
    }

    @Test
    @DisplayName("Una specifica con nome nullo o vuoto deve risultare non valida")
    void shouldRejectInvalidFunctionName() {
        FunctionSpec specWithNullName = new FunctionSpec(null, "alpine:latest", 1, 30);
        assertFalse(specWithNullName.hasValidConfiguration());

        FunctionSpec specWithBlankName = new FunctionSpec("   ", "alpine:latest", 1, 30);
        assertFalse(specWithBlankName.hasValidConfiguration());
    }

    @Test
    @DisplayName("Una specifica con timeout negativo o zero deve risultare non valida")
    void shouldRejectInvalidTimeout() {
        FunctionSpec specWithZeroTimeout = new FunctionSpec("test", "alpine:latest", 1, 0);
        assertFalse(specWithZeroTimeout.hasValidConfiguration());
    }

    @Test
    @DisplayName("updateReplicaCount deve aggiornare il valore quando il numero di repliche è valido")
    void shouldUpdateReplicaCountWhenValid() {
        FunctionSpec spec = new FunctionSpec("test", "alpine:latest", 1, 30);
        spec.updateReplicaCount(5);
        assertEquals(5, spec.getReplicas());
    }

    @Test
    @DisplayName("updateReplicaCount deve lanciare IllegalArgumentException per valori negativi")
    void shouldThrowExceptionWhenUpdatingToNegativeReplicas() {
        FunctionSpec spec = new FunctionSpec("test", "alpine:latest", 1, 30);
        assertThrows(IllegalArgumentException.class, () -> spec.updateReplicaCount(-1));
    }

    @Test
    @DisplayName("Deve calcolare correttamente i nomi di servizio e gli endpoint di rete")
    void shouldResolveCorrectNetworkEndpoints() {
        FunctionSpec spec = new FunctionSpec("echo", "alpine:latest", 2, 10);
        assertEquals("fn-echo", spec.getServiceName());
        assertEquals("http://fn-echo:8080/health", spec.getHealthUrl());
        assertEquals("http://fn-echo:8080/invoke", spec.getInvokeUrl());
    }
}
