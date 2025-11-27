package plantfall;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Set;

public class HotReloadClassLoader extends URLClassLoader {

    private final Set<String> classesToExclude;

    public HotReloadClassLoader(URL[] urls, ClassLoader parent, Set<String> classesToExclude) {
        super(urls, parent);
        this.classesToExclude = classesToExclude != null ? classesToExclude : Collections.emptySet();
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Regras de exclusão FIXAS (Java, JavaFX, classes da própria lib)
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("javafx.") ||
                name.startsWith("plantfall.")) {
            return super.loadClass(name, resolve);
        }

        // Regras de exclusão DINÂMICAS (Classes injetadas pelo usuário)
        if (classesToExclude.contains(name)) {
            return super.loadClass(name, resolve);
        }

        // Tenta SEMPRE buscar a versão nova na pasta target/classes
        try {
            Class<?> c = findClass(name);
            if (resolve) resolveClass(c);
            return c;
        } catch (ClassNotFoundException e) {
            // Se não encontrou a nova versão, volta para o ClassLoader pai
            return super.loadClass(name, resolve);
        }
    }
}