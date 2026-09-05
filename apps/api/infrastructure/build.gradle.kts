dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    // Spring Boot 4.1 partió spring-boot-autoconfigure en módulos por función;
    // flyway-database-postgresql por sí solo no trae la autoconfiguración de
    // Spring (spring-boot-flyway). Confirmado en spring-boot-dependencies:4.1.0.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Autenticación (docs/08-seguridad-legal.md). BCryptPasswordEncoder ya viene en
    // spring-security-crypto (transitivo de starter-security) — se eligió sobre Argon2id
    // precisamente para no agregar Bouncy Castle como dependencia aparte.
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JwtEncoder/JwtDecoder (Nimbus JOSE) para el JWT de acceso, en vez de una librería de
    // terceros dedicada a JWT — reutiliza solo módulos de Spring Security.
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // EnviadorDeCorreo (docs/08-seguridad-legal.md: verificación de correo, recuperación de
    // contraseña) contra Mailpit en local (docker-compose.yml) y SMTP real en producción
    // (SMTP_HOST/SMTP_USUARIO/SMTP_CLAVE, docs/07-infra-gcp.md). Mismo patrón que flyway: en
    // Spring Boot 4.1 el starter trae tanto la autoconfiguración (spring-boot-mail) como el
    // cliente (confirmado en spring-boot-dependencies:4.1.0 — a diferencia de flyway, que sí
    // necesitó el driver de base de datos aparte).
    implementation("org.springframework.boot:spring-boot-starter-mail")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x renombró los módulos con el prefijo "testcontainers-"
    // (antes org.testcontainers:junit-jupiter y :postgresql). Confirmado en el
    // testcontainers-bom:2.0.5 que trae spring-boot-dependencies:4.1.0.
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
