package plantfall;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.List;

/**
 * Classe utilitária contendo métodos estáticos para manipulação de JavaFX,
 * que são usados pelo UIReloaderImpl, mas que não precisam ser recarregáveis.
 * Esta classe deve ser parte do JAR da biblioteca.
 */
public final class FXHelper {

    private FXHelper() {
        // Utility class
    }

    /**
     * Tenta encontrar uma Stage aberta pelo seu título.
     */
    public static Stage findActiveStageByTitle(String title) {
        if (title == null || title.isEmpty()) return null;
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && title.equals(stage.getTitle())) {
                if (stage.isShowing()) {
                    return stage;
                }
            }
        }
        return null;
    }

    /**
     * Instancia uma nova Region (View) usando o construtor padrão sem argumentos.
     */
    public static Region createNewViewInstance(Class<? extends Region> viewClass) {
        try {
            return viewClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException e) {
            System.err.println("[FXHelper] Could not instantiate content view class: " + viewClass.getName());
            System.err.println("Check if the view class has a public no-argument constructor.");
            e.printStackTrace();
            return new StackPane();
        }
    }

    /**
     * Aplica uma lista de arquivos CSS a uma Scene.
     */
    public static void applyStylesToScene(Scene scene, List<String> stylesPaths, Class<?> contextClassForResources) {
        if (scene == null || stylesPaths.isEmpty()) return;

        for (String stylePath : stylesPaths) {
            try {
                // Usa a classe de contexto (originalAppClass) para carregar o recurso CSS
                // de forma correta (do classpath da aplicação).
                URL resource = contextClassForResources.getResource(stylePath);
                if (resource != null) {
                    String cssUrl = resource.toExternalForm();
                    if (!scene.getStylesheets().contains(cssUrl)) {
                        scene.getStylesheets().add(cssUrl);
                        System.out.println("[FXHelper] CSS reloaded: " + stylePath);
                    }
                } else {
                    System.err.println("[FXHelper] Resource not found: " + stylePath);
                }
            } catch (Exception e) {
                System.err.println("[FXHelper] Error loading resource: " + stylePath + " using class " + contextClassForResources.getName());
                e.printStackTrace();
            }
        }
    }
}