package plantfall;

import javafx.scene.layout.Region;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CoesionApp {
    String[] stylesheets() default {};

    // 🛑 NOVO CAMPO: Define a classe de entrada (Root View) para a UI principal
    Class<? extends Region> mainViewClass();
}