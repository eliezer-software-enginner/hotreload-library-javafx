package plantfall;

// A interface é simples e NÃO deve ter referências a JavaFX
// para evitar problemas de ClassLoader.
public interface Reloader {

    // Este método será invocado pelo HotReload.
    // Ele aceitará o objeto que contém a referência à UI (ex: App.ROOT)
    void reload(Object context);
}