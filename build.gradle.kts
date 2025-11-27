plugins {
    id("java")
    id("maven-publish") // 1. Adicionar o plugin para publicar localmente
}

group = "plantfall" // Mude este grupo
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Manter suas dependências de teste se necessário
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}


tasks.jar {
    // Define o nome do arquivo JAR gerado
    archiveBaseName.set("hotreload-library-javafx")

    // Opcional: Adiciona metadados sobre o pacote
    manifest {
        attributes(
            "Implementation-Title" to "JavaFX Hot Reload Library",
            "Implementation-Version" to project.version
        )
    }
}

// 3. Configuração de Publicação para instalar no Maven Local
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            // Define o ID do artefato para ser usado no pom.xml
            artifactId = "hotreload-library-javafx"
        }
    }
}