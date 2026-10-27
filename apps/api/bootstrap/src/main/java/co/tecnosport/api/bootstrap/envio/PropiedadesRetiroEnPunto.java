package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code RETIRO_EN_PUNTO_HABILITADO} de docs/07-infra-gcp.md. Apagada por omisión desde el 8 de
 * octubre de 2026, por decisión del negocio: ver {@code ModalidadesDeEntrega}.
 *
 * <p>Encendida en dev el 26 de octubre de 2026 junto con los términos {@code 2026-10-26.1}, que
 * vuelven a ofrecerla (ADR-0094). Encenderla en otro ambiente sin esos textos publicados sería
 * ofrecer algo que los términos niegan.
 */
@ConfigurationProperties(prefix = "tecnosport.retiro-en-punto")
public record PropiedadesRetiroEnPunto(boolean habilitado) {}
