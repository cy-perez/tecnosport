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
