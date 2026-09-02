package co.tecnosport.api.infrastructure.catalogo;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// infrastructure no depende de bootstrap, así que @SpringBootTest no encuentra la
// @SpringBootApplication real.
@SpringBootApplication
class ConfiguracionDePruebasInfraestructura {}
