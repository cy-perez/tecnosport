package co.tecnosport.api.presentation;

import org.springframework.boot.autoconfigure.SpringBootApplication;

// presentation no depende de bootstrap, así que @WebMvcTest no encuentra la @SpringBootApplication
// real.
@SpringBootApplication
class ConfiguracionDePruebasWeb {}
