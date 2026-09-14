package co.tecnosport.api.bootstrap.compartido;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra los datos que no son de ninguna funcionalidad en particular. Hoy solo {@link
 * PropiedadesApp}, que necesitan el registro, la recuperación de clave y el despacho.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesApp.class)
public class ConfiguracionApp {}
