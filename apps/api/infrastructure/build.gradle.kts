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

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x renombró los módulos con el prefijo "testcontainers-"
    // (antes org.testcontainers:junit-jupiter y :postgresql). Confirmado en el
    // testcontainers-bom:2.0.5 que trae spring-boot-dependencies:4.1.0.
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
