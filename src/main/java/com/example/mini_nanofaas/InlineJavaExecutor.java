package com.example.mini_nanofaas;

import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compila ed esegue dinamicamente funzioni Java scritte inline dalla GUI.
 * <p>
 * Assunzioni e limiti operativi per il debug:
 * <ul>
 *   <li>Richiede l'esecuzione su un JDK completo (su JRE {@link ToolProvider#getSystemJavaCompiler()}
 *       restituisce {@code null}).</li>
 *   <li>Esegue il codice nello stesso processo JVM del controller, senza sandbox o isolamento di sicurezza.</li>
 *   <li>Non mantiene cache di compilazione né classloader persistenti: ogni chiamata a {@link #execute}
 *       ricompila da zero con {@code javac}, quindi eventuali campi {@code static} non conservano
 *       stato tra invocazioni successive e ogni chiamata paga la latenza di compilazione.</li>
 * </ul>
 */
@Component
public class InlineJavaExecutor {

    // Parsing lessicale basato su regex senza stripping di commenti o stringhe letterali:
    // un commento contenente "public class X" prima della classe reale fa salvare il file come X.java
    // causando errori javac. Inoltre PUBLIC_CLASS_PATTERN non matcha modificatori intermedi
    // (es. "public final class Foo"), che ricadono sul fallback ANY_CLASS_PATTERN.
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("\\bpublic\\s+class\\s+(\\w+)");
    private static final Pattern ANY_CLASS_PATTERN = Pattern.compile("\\bclass\\s+(\\w+)");

    /**
     * Verifica che il codice sorgente Java compili correttamente senza eseguirlo.
     * <p>
     * Nota: controlla solo la compilabilità con {@code javac}; non verifica né la presenza di
     * {@code handle(String)} né l'assenza di dichiarazioni {@code package}.
     *
     * @param sourceCode codice sorgente Java
     * @throws IllegalArgumentException se la compilazione fallisce o manca una dichiarazione {@code class} (HTTP 400 in register)
     * @throws IllegalStateException    se manca il compilatore JDK (JRE) o si verifica un errore I/O (HTTP 500 in register)
     */
    public void validateCompilation(String sourceCode) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("nanofaas-validate-");
            compileToDirectory(sourceCode, tempDir);
        } catch (IOException e) {
            // Mappato a IllegalStateException (non IllegalArgumentException) perché indica un problema
            // di ambiente/filesystem lato server (HTTP 500), non un sorgente invalido del client.
            throw new IllegalStateException("Errore I/O durante la compilazione: " + e.getMessage(), e);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    /**
     * Compila ed esegue il metodo {@code handle(String)} della funzione Java fornita.
     *
     * @param sourceCode     codice sorgente Java
     * @param payload        input testuale passato a {@code handle(String)}
     * @param timeoutSeconds timeout massimo di esecuzione in secondi
     * @return risultato convertito in stringa
     */
    public String execute(String sourceCode, String payload, int timeoutSeconds) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("nanofaas-exec-");
            String className = compileToDirectory(sourceCode, tempDir);

            // Il caricatore consulta prima quello dell'applicazione. Se il nome coincide con una
            // classe già visibile al padre, Class.forName può risolvere quella invece del bytecode appena compilato.
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{tempDir.toUri().toURL()},
                    InlineJavaExecutor.class.getClassLoader()
            )) {
                // ATTENZIONE: Class.forName(..., true, ...) esegue i blocchi `static {}` e
                // newInstance() esegue il costruttore no-arg sul thread HTTP del chiamante, FUORI dal
                // timeout di invokeWithTimeout(). Inoltre `className` è privo di package: una
                // dichiarazione `package ...;` fa fallire Class.forName con ClassNotFoundException (500).
                // Nota contratto: il messaggio d'errore cita `public static String handle(String)`, ma
                // qui sono accettati anche metodi di istanza (se il costruttore no-arg è accessibile)
                // e tipi di ritorno diversi da String (convertiti via String.valueOf).
                Class<?> fnClass = Class.forName(className, true, classLoader);
                Method handleMethod = fnClass.getMethod("handle", String.class);
                Object instance = Modifier.isStatic(handleMethod.getModifiers())
                        ? null
                        : fnClass.getDeclaredConstructor().newInstance();

                return invokeWithTimeout(handleMethod, instance, payload, timeoutSeconds);
            }
        } catch (IllegalArgumentException | FunctionTimeoutException e) {
            throw e;
        } catch (NoSuchMethodException e) {
            // Errore imputabile al codice utente (firma assente/non pubblica) -> HTTP 400 su :invoke
            throw new IllegalArgumentException(
                    "La classe deve definire il metodo: public static String handle(String input)"
            );
        } catch (Exception e) {
            // Include eccezioni del metodo (ExecutionException), errori riflessivi del costruttore
            // e InterruptedException su future.get (il flag interrupt del thread chiamante non è ripristinato).
            throw new IllegalStateException("Errore durante l'esecuzione della funzione: " + e.getMessage(), e);
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private String invokeWithTimeout(Method method, Object instance, String payload, int timeoutSeconds)
            throws ExecutionException, InterruptedException {
        // Dopo cancel(true), close() attende l'uscita del worker anche durante la propagazione
        // dell'eccezione. Finché handle ignora l'interruzione e continua a girare, il controller
        // resta in attesa: sia la risposta 504 sia lo scaling a zero sono ritardati.
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Object> future = executor.submit(() -> {
                try {
                    return method.invoke(instance, payload);
                } catch (InvocationTargetException e) {
                    // Scompatta l'eccezione reale della funzione utente nascosta da Method.invoke
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException(cause.getMessage(), cause);
                }
            });

            try {
                Object result = future.get(timeoutSeconds, TimeUnit.SECONDS);
                return result == null ? "" : String.valueOf(result);
            } catch (TimeoutException e) {
                // Invia solo interrupt al thread worker: non forza l'arresto di codice CPU-bound che ignora l'interrupt
                future.cancel(true);
                throw new FunctionTimeoutException("Timeout superato (" + timeoutSeconds + "s): funzione disattivata (0 repliche).");
            }
        }
    }

    private String compileToDirectory(String sourceCode, Path outputDir) throws IOException {
        // Restituisce null se l'app gira su una JRE (es. stage runtime del Dockerfile senza JDK):
        // in quel caso sia la registrazione sia l'invocazione inline falliscono con HTTP 500.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "Compilatore Java non disponibile: avvia l'applicazione con un JDK (non solo JRE)."
            );
        }

        // javac richiede che il file sorgente abbia esattamente il nome della classe public e usa UTF-8
        // esplicito per evitare dipendenze dal charset di default del container/OS.
        String className = extractClassName(sourceCode);
        Path sourceFile = outputDir.resolve(className + ".java");
        Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-d", outputDir.toString()),
                    null,
                    compilationUnits
            );

            boolean success = Boolean.TRUE.equals(task.call());
            if (!success) {
                // Filtra solo gli ERROR ignorando eventuali WARNING/NOTE del compilatore
                String errors = diagnostics.getDiagnostics().stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .map(d -> "Riga " + d.getLineNumber() + ": " + d.getMessage(null))
                        .collect(Collectors.joining("\n"));
                throw new IllegalArgumentException("Errore di compilazione Java:\n" + errors);
            }
        }
        return className;
    }

    private String extractClassName(String sourceCode) {
        // Preferisce la classe `public` (vincolo javac sul nome file) e ripiega sulla prima `class`
        // trovata; non supporta file con soli record/interface né distingue commenti pregressi.
        Matcher publicMatcher = PUBLIC_CLASS_PATTERN.matcher(sourceCode);
        if (publicMatcher.find()) {
            return publicMatcher.group(1);
        }
        Matcher anyMatcher = ANY_CLASS_PATTERN.matcher(sourceCode);
        if (anyMatcher.find()) {
            return anyMatcher.group(1);
        }
        throw new IllegalArgumentException("Il codice Java deve contenere almeno una dichiarazione 'class ...'");
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        // Ordine inverso necessario per eliminare file e sottocartelle prima della directory padre;
        // gli IOException sono ignorati nel finally per non mascherare l'errore principale di esecuzione/timeout.
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Ignora errori di pulizia su file temporanei
                        }
                    });
        } catch (IOException ignored) {
            // Ignora errori di pulizia su directory temporanea
        }
    }

    /**
     * Eccezione sollevata quando l'esecuzione di una funzione supera il timeout configurato.
     */
    public static class FunctionTimeoutException extends RuntimeException {
        public FunctionTimeoutException(String message) {
            super(message);
        }
    }
}
