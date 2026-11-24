package com.example.mini_nanofaas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test di integrazione HTTP end-to-end per la GUI web e gli endpoint CRUD di {@link FunctionController}.
 * <p>
 * Nota di debugging: tutti i test condividono un unico contesto Spring e un unico database H2 in-memory
 * (assenza di {@code @DirtiesContext} o cleanup in {@code @BeforeEach}). L'isolamento si regge su nomi
 * funzione univoci e sul {@code DELETE} finale: se un test fallisce a metà, la riga residua resta in H2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FunctionControllerIntegrationTest {

    @LocalServerPort
    private int port;

    // Sostituisce LocalProvider nel contesto Spring: in POST /v1/functions viene bypassato
    // LocalProvider.deploy() -> inlineJavaExecutor.validateCompilation() (errori di compilazione
    // al deploy non sono coperti qui). I metodi boolean vanno stubbati a true per evitare HTTP 500.
    @MockitoBean
    private ProviderFunzione functionProvider;

    // Isola il percorso Docker/Swarm (http://fn-<name>:8080/invoke) per testare il routing HTTP senza container.
    @MockitoBean
    private FunctionDispatcher functionDispatcher;

    // Client HTTP reale senza request timeout lato client: se il server si blocca (es. thread non interrompibile), attende.
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // Verifica il solo serving statico di index.html su "/": non esegue JavaScript né verifica il DOM.
    @Test
    void webGuiIsServedAtRoot() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/"))).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("mini_nanofaas"));
        assertTrue(response.body().contains("/v1/functions"));
    }

    @Test
    void fullCrudAndInvokeLifecycleViaHttp() throws Exception {
        // Funzione solo-immagine (senza sourceCode): :invoke instrada su FunctionDispatcher, non su InlineJavaExecutor.
        // Nota: gli stub non usano verify(...) e il DELETE finale non è in try/finally (un failure lascia la riga in H2).
        when(functionProvider.deploy(any(FunctionSpec.class))).thenReturn(true);
        when(functionProvider.scale(anyString(), anyInt())).thenReturn(true);
        when(functionProvider.remove(anyString())).thenReturn(true);
        when(functionDispatcher.dispatch(anyString(), any())).thenReturn("ok-from-fn");

        // 1. Create (POST /v1/functions)
        String payload = """
                {
                  "name": "gui-test-fn",
                  "imageDigest": "ghcr.io/example/fn:1.0",
                  "replicas": 1,
                  "timeoutSeconds": 10
                }
                """;
        HttpRequest createReq = HttpRequest.newBuilder(URI.create(url("/v1/functions")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> createRes = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRes.statusCode());

        // 2. Read (GET /v1/functions)
        HttpRequest listReq = HttpRequest.newBuilder(URI.create(url("/v1/functions"))).GET().build();
        HttpResponse<String> listRes = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listRes.statusCode());
        assertTrue(listRes.body().contains("gui-test-fn"));

        // 3. Update / Scale (POST /v1/functions/{name}/scale?replicas=3)
        HttpRequest scaleReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/gui-test-fn/scale?replicas=3")))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> scaleRes = httpClient.send(scaleReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, scaleRes.statusCode());

        // 4. Invoke (POST /v1/functions/{name}:invoke)
        HttpRequest invokeReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/gui-test-fn:invoke")))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("hello"))
                .build();
        HttpResponse<String> invokeRes = httpClient.send(invokeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, invokeRes.statusCode());
        assertEquals("ok-from-fn", invokeRes.body());

        // 5. Delete (DELETE /v1/functions/{name})
        HttpRequest deleteReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/gui-test-fn")))
                .DELETE()
                .build();
        HttpResponse<String> deleteRes = httpClient.send(deleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteRes.statusCode());
    }

    @Test
    void inlineJavaFunctionCompilesAndExecutes() throws Exception {
        // Richiede un JDK completo (ToolProvider.getSystemJavaCompiler() != null): su un JRE restituisce HTTP 500.
        // Con ProviderFunzione mockato, la compilazione è verificata solo su :invoke (non durante POST /v1/functions).
        when(functionProvider.deploy(any(FunctionSpec.class))).thenReturn(true);
        when(functionProvider.remove(anyString())).thenReturn(true);

        // Specifica imageDigest esplicito: non copre la normalizzazione automatica di imageDigest blank in register().
        String payload = """
                {
                  "name": "upper-java",
                  "imageDigest": "inline-java",
                  "sourceCode": "public class Function { public static String handle(String input) { return input == null ? \\"EMPTY\\" : input.toUpperCase(); } }",
                  "replicas": 1,
                  "timeoutSeconds": 5
                }
                """;
        HttpRequest createReq = HttpRequest.newBuilder(URI.create(url("/v1/functions")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> createRes = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRes.statusCode());

        HttpRequest invokeReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/upper-java:invoke")))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("ciao"))
                .build();
        HttpResponse<String> invokeRes = httpClient.send(invokeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, invokeRes.statusCode());
        assertEquals("CIAO", invokeRes.body());

        HttpRequest deleteReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/upper-java")))
                .DELETE()
                .build();
        assertEquals(200, httpClient.send(deleteReq, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void timeoutDeactivatesFunctionWithoutDeletingRegistration() throws Exception {
        // Il ramo timeout ignora il booleano di scale(). Le asserzioni osservano la risposta
        // HTTP e il catalogo H2; un problema nello scaling reale richiede una verifica sul provider.
        when(functionProvider.deploy(any(FunctionSpec.class))).thenReturn(true);
        when(functionProvider.scale(anyString(), anyInt())).thenReturn(true);
        when(functionProvider.remove(anyString())).thenReturn(true);

        // Thread.sleep(3000) è intenzionale: risponde a InterruptedException su future.cancel(true) dopo timeoutSeconds=1.
        // Codice non interrompibile (es. while(true){}) bloccherebbe ExecutorService.close() impedendo il ritorno del 504.
        String payload = """
                {
                  "name": "slow-java",
                  "imageDigest": "inline-java",
                  "sourceCode": "public class Function { public static String handle(String input) throws Exception { Thread.sleep(3000); return \\"done\\"; } }",
                  "replicas": 1,
                  "timeoutSeconds": 1
                }
                """;
        HttpRequest createReq = HttpRequest.newBuilder(URI.create(url("/v1/functions")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        assertEquals(201, httpClient.send(createReq, HttpResponse.BodyHandlers.ofString()).statusCode());

        HttpRequest invokeReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/slow-java:invoke")))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("go"))
                .build();
        HttpResponse<String> invokeRes = httpClient.send(invokeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(504, invokeRes.statusCode());

        // La funzione deve restare registrata nel catalogo ma con 0 repliche (non attiva).
        // Nota: le due contains() verificano sottostringhe indipendenti sull'intero array JSON (non per singolo oggetto)
        // e assumono formattazione Jackson compatta senza spazi; in caso di residui in H2 un altro record a 0 repliche falserebbe il check.
        HttpRequest listReq = HttpRequest.newBuilder(URI.create(url("/v1/functions"))).GET().build();
        HttpResponse<String> listRes = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listRes.statusCode());
        assertTrue(listRes.body().contains("\"name\":\"slow-java\""));
        assertTrue(listRes.body().contains("\"replicas\":0"));

        HttpRequest deleteReq = HttpRequest.newBuilder(URI.create(url("/v1/functions/slow-java")))
                .DELETE()
                .build();
        assertEquals(200, httpClient.send(deleteReq, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
