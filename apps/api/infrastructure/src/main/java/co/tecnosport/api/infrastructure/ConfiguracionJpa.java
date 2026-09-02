package co.tecnosport.api.infrastructure;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * {@code @EnableJpaRepositories} y {@code @EntityScan} no siguen el {@code scanBasePackages} de
 * {@code @SpringBootApplication}: por defecto cada uno solo mira el paquete de la clase principal
 * ({@code co.tecnosport.api.bootstrap}), nunca {@code infrastructure}. Esta clase vive aquí, no en
 * {@code bootstrap}, para que bootstrap no necesite saber que existe JPA — es responsabilidad de
 * infrastructure declarar cómo se descubren sus propias entidades y repositorios.
 */
@Configuration
@EnableJpaRepositories(basePackages = "co.tecnosport.api.infrastructure")
@EntityScan(basePackages = "co.tecnosport.api.infrastructure")
public class ConfiguracionJpa {}
