// my_app/App.java (Inicialização)
// ...
@Override
public void start(Stage stage) {
// ... (criação do stage/scene)


    // 1. Definições a serem injetadas
    String implClass = "my_app.UIReloaderImpl";
    String resourcesPath = "src/main/resources";
    Set<String> exclusions = new HashSet<>();
    exclusions.add("my_app.App"); // A classe App não deve ser recarregada

    // 2. Inicializa o HotReload
    hotReload = new HotReload(
        "src/main/java/my_app", 
        "target/classes", 
        resourcesPath,
        implClass,
        ROOT, // A referência StackPane ROOT é o contexto
        exclusions
    );
    hotReload.start();



}
// ...