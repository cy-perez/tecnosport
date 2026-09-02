package co.tecnosport.api.infrastructure;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// infrastructure no depende de bootstrap, así que @SpringBootTest no encuentra la
// @SpringBootApplication real. Vive en la raíz del paquete de pruebas de infrastructure (no en un
// sub-paquete como catalogo) para que la búsqueda hacia arriba de @SpringBootTest la encuentre
// desde cualquier funcionalidad (catalogo, inventario, carrito...), no solo desde la primera.
@SpringBootApplication
class ConfiguracionDePruebasInfraestructura {}
