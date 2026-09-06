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

// Solo para desarrollo local: activa SembradorCatalogo (@Profile("local")).
// No afecta el jar empaquetado que corre en Cloud Run.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "local")
}
