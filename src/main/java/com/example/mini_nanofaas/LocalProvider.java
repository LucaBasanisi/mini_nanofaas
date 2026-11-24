package com.example.mini_nanofaas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Provider locale attivo di default quando {@code nanofaas.provider} non è impostata
 * ({@code matchIfMissing = true}) o vale {@code local}.
 * <p>
 * Invariante di configurazione: {@link LocalProvider} e {@link SwarmProvider} sono gli unici
 * bean che implementano {@link ProviderFunzione}. Poiché {@code application.properties} non
 * definisce {@code nanofaas.provider}, questo bean è attivo anche nei test (es.
 * {@code MiniNanofaasApplicationTests}) e nei container avviati dal {@code Dockerfile}
 * senza {@code -e NANOFAAS_PROVIDER=swarm}; se la proprietà viene impostata a un valore
 * diverso da {@code local} o {@code swarm}, nessuno dei due bean viene creato e Spring
 * fallisce all'avvio con {@code NoSuchBeanDefinitionException} su {@link FunctionController}.
 */
@Service
@ConditionalOnProperty(name = "nanofaas.provider", havingValue = "local", matchIfMissing = true)
public class LocalProvider implements ProviderFunzione {

    private static final Logger log = LoggerFactory.getLogger(LocalProvider.class);

    private final InlineJavaExecutor inlineJavaExecutor;

    public LocalProvider(InlineJavaExecutor inlineJavaExecutor) {
        this.inlineJavaExecutor = inlineJavaExecutor;
    }

    @Override
    public boolean deploy(FunctionSpec function) {
        // Verifica SOLO la compilazione javac: l'eventuale assenza di handle(String), di un
        // costruttore accessibile o la presenza di "package ..." emergerà solo a :invoke (400/500).
        // Nota HTTP in FunctionController.register(): IllegalArgumentException -> 400 Bad Request,
        // mentre IllegalStateException (es. JDK assente / JRE senza javac) -> 500 Internal Server Error.
        if (function.hasSourceCode()) {
            inlineJavaExecutor.validateCompilation(function.getSourceCode());
        }
        // Per funzioni senza sourceCode (immagini container) il deploy locale è un no-op: nessun
        // container viene avviato, quindi un successivo :invoke instradato su FunctionDispatcher
        // tenterà comunque una chiamata HTTP a http://fn-<name>:8080/invoke e fallirà fuori da Swarm.
        log.info("[LOCAL] Deploy completato per '{}' (repliche: {})", function.getName(), function.getReplicas());
        return true;
    }

    // Per una funzione inline, la modifica diventa visibile alla prossima invocazione
    // quando il controller aggiorna H2. Portare le repliche a zero lascia finire le chiamate già avviate.
    @Override
    public boolean remove(String functionName) {
        log.info("[LOCAL] Rimozione completata per '{}'", functionName);
        return true;
    }

    @Override
    public boolean scale(String functionName, int replicas) {
        log.info("[LOCAL] Scaling completato per '{}' -> {} repliche", functionName, replicas);
        return true;
    }
}
