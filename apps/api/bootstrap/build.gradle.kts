plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(project(":presentation"))

    implementation("org.springframework.boot:spring-boot-starter")
    // ConfiguracionIdempotencia registra un Filter (jakarta.servlet) atado a rutas concretas —
    // necesita el tipo en su propio classpath de compilación, no solo en el de presentation
    // ("implementation" no expone dependencias transitivas a quien depende del módulo).
    implementation("org.springframework.boot:spring-boot-starter-web")
    // TareaConciliacionWompi abre su propia transacción (TransactionTemplate), mismo motivo que
    // spring-boot-starter-web arriba: necesita el tipo en el classpath propio de bootstrap.
    implementation("org.springframework:spring-tx")
    // ConfiguracionSeguridad arma el SecurityFilterChain (HttpSecurity), mismo motivo que
    // spring-boot-starter-web arriba.
    implementation("org.springframework.boot:spring-boot-starter-security")
    // ConfiguracionCorreo referencia JavaMailSender directo, mismo motivo que
    // spring-boot-starter-web arriba.
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // ConfiguracionCatalogo arma el bean Storage (StorageOptions.getDefaultInstance()), mismo
    // motivo que spring-boot-starter-web arriba. Misma versión que infrastructure.
    implementation("com.google.cloud:google-cloud-storage:2.71.0")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
}

/**
 * Lee un archivo de tipo `.env`: una variable por línea, `CLAVE=valor`, con
 * comentarios que empiezan por `#`. No expande variables ni interpreta comillas
 * más allá de quitarlas de los extremos — si algún día hace falta más que eso,
 * es señal de que el valor no debería estar en un archivo plano.
 *
 * **Una clave con valor vacío se omite**, no se pasa como cadena vacía:
 * `.env.example` trae varias así a propósito (`CONTRAENTREGA_MONTO_MAXIMO=`,
 * `SMTP_USUARIO=`) queriendo decir "sin definir, usa el valor por defecto".
 * Pasarlas vacías rompe el arranque, porque para Spring la variable existe y
 * `application.yml` ya no aplica su valor por defecto: una propiedad de tipo
 * primitivo (`long`) recibe null y falla al enlazar.
 */
fun leerVariablesDeEntorno(archivo: java.io.File): Map<String, String> {
    if (!archivo.exists()) {
        return emptyMap()
    }
    return archivo
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { linea ->
            val separador = linea.indexOf('=')
            val clave = linea.take(separador).trim()
            val valor = linea.substring(separador + 1).trim().removeSurrounding("\"")
            clave to valor
        }
        .filterValues { it.isNotEmpty() }
}

// Solo para desarrollo local: activa SembradorCatalogo (@Profile("local")).
// No afecta el jar empaquetado que corre en Cloud Run.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "local")

    // Spring Boot lee variables de entorno del proceso, no archivos `.env`: sin
    // esto, `.env.local` no tenía ningún efecto sobre `bootRun` y toda la
    // configuración local salía de los valores por defecto de application.yml —
    // aunque README.md y apps/api/README.md dijeran que las variables viven ahí.
    // En producción no aplica: Cloud Run inyecta el entorno de verdad y este
    // bloque solo existe en la tarea de desarrollo (docs/07-infra-gcp.md).
    val archivoEnv = rootProject.projectDir.parentFile.parentFile.resolve(".env.local")
    doFirst {
        // Una variable ya exportada en la terminal gana sobre el archivo, que es
        // el contrato habitual de dotenv: permite cambiar un valor por una sola
        // corrida sin editar `.env.local`.
        val variables = leerVariablesDeEntorno(archivoEnv).filterKeys { System.getenv(it) == null }
        environment(variables)
        // Solo el conteo: los valores son secretos y no van a un log.
        if (variables.isEmpty()) {
            logger.lifecycle("bootRun: sin variables de ${archivoEnv.name}; se usan los valores por defecto de application.yml.")
        } else {
            logger.lifecycle("bootRun: ${variables.size} variables cargadas de ${archivoEnv.name}.")
        }
    }
}
