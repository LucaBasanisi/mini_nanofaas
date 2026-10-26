package com.example.mini_nanofaas;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FunctionDispatcher {
    /*
    Il FunctionController avvia il metodo "invoke" e lo invia al FunctionDispatcher
    - costruiamo l'indirizzo di rete attorno al container
    - facciamo una chiamata HTTP al container usando RestClient
    - inviamo la risposta al controller
     */


    /*
    Scriviamo il costruttore, però non va bene scriverlo così
    public FunctionDispatcher(RestClient restClient) {
        this.restClient = restClient;
    }
    Per costruire FunctionDispatcher, ho bisogno di un oggetto di tipo RestClient. Vado a cercarne uno nel mio magazzino!"
    Spring Boot pre-configura tantissime cose automaticamente, ma non crea un
    RestClient "finito". Perché? Perché in un'applicazione reale, potresti voler chiamare più servizi diversi
    con configurazioni diverse:
  • Un servizio con timeout a 2 secondi e autenticazione OAuth.
  • Un altro servizio con timeout a 30 secondi e certificato SSL custom.
    Se Spring creasse un unico RestClient globale nel magazzino, non saprebbe con quali impostazioni lo vuoi!

    Spring Boot allora mette nel magazzino un "kit di montaggio" già parzialmente configurato:
    il RestClient.Builder.
    */
    public FunctionDispatcher(RestClient.Builder builder) {
        this.restClient = builder.build();
    }
    private final RestClient restClient;

    // Metodo dispatch
    // String url = "http://fn-" + functionName + ":8080/invoke";
    // (Ad esempio, se functionName è "saluta", allora http://fn-saluta:8080/invoke).
    public String dispatch(String functionName, String payload) {

        // creiamo la richiesta/chiamata per il container
        var request = restClient.post()
                .uri(getUrl(functionName));

        // Se esiste il payload lo aggiungiamo, altrimenti no
        if(payload != null) {
            request.body(payload);
        }

        // Eseguiamo la chiamata
        return request.retrieve()
                .body(String.class);
    }


    private static @NonNull String getUrl(String functionName) {
        return "http://fn-" + functionName + ":8080/invoke";
    }

}
