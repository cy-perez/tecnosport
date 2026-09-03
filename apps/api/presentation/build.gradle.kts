dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    // El controlador de pedidos abre la transacción que comparten la reserva de inventario
    // (bloqueo pesimista) y el guardado del pedido — ver el javadoc de RepositorioPedidosJpa.
    // No es infraestructura propia del proyecto, es un tipo del framework, igual que
    // spring-boot-starter-web.
    implementation("org.springframework:spring-tx")
    // Sin versión gestionada por el BOM de Spring Boot (springdoc no es de
    // Spring): la más reciente en Maven Central hoy es la misma línea 2.8.x
    // que ya soportaba Boot 3. Si su autoconfiguración no arranca con Boot
    // 4.1/Framework 7, se retira y queda anotado — no hay una versión más
    // nueva que probar todavía.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
