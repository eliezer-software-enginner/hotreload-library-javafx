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

    private final Path resourcesPath;

    /**
     * @param src O caminho para os arquivos .java (ex: "src/main/java").
     * @param classes O caminho para o output da compilação (ex: "target/classes").
     * @param res O caminho para os arquivos de recurso (ex: "src/main/resources").
     * @param implClassName O nome completo da classe que implementa IReloadable (ex: "my_app.UIReloaderImpl").
     * @param reloadContext A referência do objeto a ser passada para IReloadable.reload() (ex: Stage principal).
     * @param classesToExclude Classes/interfaces que NÃO devem ser recarregadas.
     */
    public HotReload(String src, String classes, String res,
                     String implClassName, Object reloadContext, Set<String> classesToExclude) {
        this.sourcePath = Paths.get(src);
        this.classesPath = Paths.get(classes);
        this.resourcesPath = Paths.get(res);
        this.implementationClassName = implClassName;
        this.reloadContext = reloadContext;
        this.classesToExclude = classesToExclude;
        // Adiciona a interface de biblioteca para evitar ClassCastException (regra 1)
        this.classesToExclude.add(Reloader.class.getName());

        // 💥 NOVO: Excluir a Annotation para garantir que o System Class Loader a veja
        // (Ainda precisa usar o FQCN, pois ela está no mesmo pacote da aplicação por enquanto)
        //this.classesToExclude.add("my_app.CoesionApp");
        //this.classesToExclude.add("my_app.ReloadableWindow");
        //this.classesToExclude.add("my_app.Reloader");
    }

    public void start() {
        Thread t = new Thread(this::watchLoop, "HotReload-Watcher");
        t.setDaemon(true);
        t.start();

        // 🛑 Lógica de Inicialização Automática (Bootstrapping da ID)
        // Chamamos callReloadEntry() imediatamente para forçar a primeira injeção
        // de dependência (Stage window) e a execução do initView().
        try {
            System.out.println("[HotReload] Performing initial UI setup and Dependency Injection...");
            callReloadEntry();
        } catch (Exception e) {
            System.err.println("[HotReload] Failed during initial setup call.");
            e.printStackTrace();
        }
    }

    private void watchLoop() {
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {

            // 1. Registra o Source Path (código Java)
            sourcePath.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

            // 2. Registra o Resources Path (recursos)
            resourcesPath.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);

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

                        // Adicionar um filtro para ignorar arquivos temporários ou de backup
                        if (changedFile.getFileName().toString().endsWith("~")) {
                            System.out.println("[HotReload] Ignored temporary file: " + changedFile);
                            continue; // Pula este evento
                        }

                        // ***********************************************
                        // TRUQUE VITAL: Copiar o recurso de resources para classes
                        // ***********************************************
                        Path targetCss = classesPath.resolve(changedFile.getFileName());

                        try {
                            // Força a cópia, sobrescrevendo o arquivo antigo
                            Files.copy(changedFile, targetCss, StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("[HotReload] CSS copied to target/classes.");
                            callReloadEntry();
                        } catch (IOException e) {
                            System.err.println("[HotReload] Failed to copy CSS: " + e.getMessage());
                        }
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
        var reloader = (Reloader) reloaderClass.getDeclaredConstructor().newInstance();

        System.out.println("[HotReload] Invoking new Reloader implementation: " + implementationClassName);

        // Usamos reflection para chamar o Platform.runLater do JavaFX para não depender diretamente do módulo javafx.controls.
        Class<?> platformClass = Class.forName("javafx.application.Platform");
        Method runLaterMethod = platformClass.getMethod("runLater", Runnable.class);

        runLaterMethod.invoke(null, (Runnable) () -> {
            try {
                // Passa o objeto de contexto injetado (Stage principal)
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