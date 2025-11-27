package plantfall;


import javax.tools.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public class HotReload {

    private final Path sourcePath;
    private final Path classesPath;
    private final String implementationClassName;
    private final Object reloadContext;
    private final Set<String> classesToExclude; // Novo parâmetro para o ClassLoader

    private volatile boolean running = true;

    /**
     * @param src O caminho para os arquivos .java (ex: "src/main/java").
     * @param classes O caminho para o output da compilação (ex: "target/classes").
     * @param implClassName O nome completo da classe que implementa IReloadable (ex: "my_app.UIReloaderImpl").
     * @param reloadContext A referência do objeto a ser passada para IReloadable.reload() (ex: App.ROOT).
     * @param classesToExclude Classes/interfaces que NÃO devem ser recarregadas (ex: "my_app.IReloadable").
     */

    private final Path resourcesPath;
    public HotReload(String src, String classes, String res,
                     String implClassName, Object reloadContext, Set<String> classesToExclude) {
        this.sourcePath = Paths.get(src);
        this.classesPath = Paths.get(classes);
        this.resourcesPath = Paths.get(src);
        this.implementationClassName = implClassName;
        this.reloadContext = reloadContext;
        this.classesToExclude = classesToExclude;
        // Adiciona a interface de biblioteca para evitar ClassCastException (regra 1)
        this.classesToExclude.add(IReloadable.class.getName());
    }

    public void start() {
        Thread t = new Thread(this::watchLoop, "HotReload");
        t.setDaemon(true);
        t.start();
    }

    // [watchLoop e compile permanecem os mesmos]
    // ... (watchLoop)
    // ... (compile)

// plantfall/HotReload.java

    private void watchLoop() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {

            // 1. Registra o Source Path (código Java)
            sourcePath.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

            // 2. Registra o Resources Path (recursos)
            resourcesPath.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE); // NOVO REGISTRO

            System.out.println("[HotReload] started, watching Java source: " + sourcePath + " and Resources: " + resourcesPath);

            while (running) {
                WatchKey key = ws.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (!event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY) &&
                            !event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE))
                        continue;

                    Path changedFolder = (Path) key.watchable(); // Pasta que sofreu a mudança (sourcePath ou resourcesPath)
                    Path changedFile = changedFolder.resolve((Path) event.context());

                    if (changedFolder.equals(sourcePath) && changedFile.toString().endsWith(".java")) {
                        // Mudança em arquivo .java: COMPILA + RECARREGA
                        System.out.println("[HotReload] Java Change detected: " + changedFile);
                        if (compile()) {
                            callReloadEntry();
                        }
                    } else if (changedFolder.equals(resourcesPath)) {
                        // Mudança em arquivo de recurso (ex: .css): RECARREGA (não compila)
                        System.out.println("[HotReload] Resource Change detected: " + changedFile);
                        // Aqui você precisa garantir que o sistema de build do seu projeto CLIENTE
                        // copiou o recurso modificado de src/main/resources para target/classes!
                        callReloadEntry();
                    }
                }
                key.reset();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean compile() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("[HotReload] No Java compiler available.");
            return false;
        }

        // listar todos os arquivos .java
        List<String> files = new ArrayList<>();
        Files.walk(sourcePath)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> files.add(p.toString()));

        System.out.println("[HotReload] Compiling...");

        // argumentos do javac DEVEM ser separados
        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(classesPath.toString());
        args.addAll(files);

        int result = compiler.run(null, null, null,
                args.toArray(new String[0]));

        System.out.println("[HotReload] Compile status: " + (result == 0));
        return result == 0;
    }


    private void callReloadEntry() throws Exception {
        URL[] urls = new URL[]{classesPath.toUri().toURL()};

        // Novo: Passa as classes a serem excluídas para o ClassLoader
        ClassLoader cl = new HotReloadClassLoader(urls, ClassLoader.getSystemClassLoader(), classesToExclude);

        // Carrega a classe de recarga NO NOVO ClassLoader, usando o nome da classe injetada
        Class<?> reloaderClass = cl.loadClass(implementationClassName);

        // Cria uma nova instância da classe de recarga
        IReloadable reloader = (IReloadable) reloaderClass.getDeclaredConstructor().newInstance();

        System.out.println("[HotReload] Invoking new Reloader implementation: " + implementationClassName);

        // A execução deve ser feita na thread de UI (JavaFX, Swing, etc.)
        // O cliente deve fornecer uma forma de rodar isso na thread correta.
        // Já que você está em JavaFX, manteremos o Platform.runLater, mas idealmente seria injetado.

        // Usamos reflection para chamar o Platform.runLater do JavaFX para não depender diretamente do módulo javafx.controls.
        Class<?> platformClass = Class.forName("javafx.application.Platform");
        Method runLaterMethod = platformClass.getMethod("runLater", Runnable.class);

        runLaterMethod.invoke(null, (Runnable) () -> {
            try {
                // Passa o objeto de contexto injetado (ex: App.ROOT)
                reloader.reload(reloadContext);
                System.out.println("[HotReload] Reload finished.");
            } catch (Exception e) {
                System.err.println("[HotReload] Error during reload execution.");
                e.printStackTrace();
            }
        });
    }

    public void stop() {
        running = false;
    }
}