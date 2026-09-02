plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(project(":presentation"))

    implementation("org.springframework.boot:spring-boot-starter")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
}

// Solo para desarrollo local: activa SembradorCatalogo (@Profile("local")).
// No afecta el jar empaquetado que corre en Cloud Run.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "local")
}
