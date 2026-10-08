package co.tecnosport.api.bootstrap.envio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code RETIRO_EN_PUNTO_HABILITADO} de docs/07-infra-gcp.md. Apagada por omisión desde el 8 de
 * octubre de 2026, por decisión del negocio: ver {@code ModalidadesDeEntrega}.
 *
 * <p>Encenderla no basta: los términos (§4 y §8), las preguntas frecuentes, la página de contacto y
 * el carrito dejaron de prometer la recogida cuando se apagó, y hay que devolverles el texto con la
 * skill de textos legales antes de cambiar esta variable.
 */
@ConfigurationProperties(prefix = "tecnosport.retiro-en-punto")
public record PropiedadesRetiroEnPunto(boolean habilitado) {}
