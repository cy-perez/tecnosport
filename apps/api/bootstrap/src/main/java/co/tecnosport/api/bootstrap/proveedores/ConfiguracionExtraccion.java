package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.proveedores.ArmarPublicaciones;
import co.tecnosport.api.application.proveedores.ExtractorDeProductos;
import co.tecnosport.api.application.proveedores.ExtraerProductoDePublicacion;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.infrastructure.proveedores.extraccion.ExtractorClaude;
import co.tecnosport.api.infrastructure.proveedores.extraccion.ExtractorSembrado;
import co.tecnosport.api.infrastructure.proveedores.extraccion.RecursosDelExtractor;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La agrupación y la extracción. Aparte de {@code ConfiguracionProveedores} por tamaño, no por
 * capa.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesExtraccion.class)
public class ConfiguracionExtraccion {

  private static final Logger log = LoggerFactory.getLogger(ConfiguracionExtraccion.class);

  /**
   * El de verdad solo con clave. El aviso al arrancar es para saber en qué modo quedó la instancia.
   */
  @Bean
  public ExtractorDeProductos extractorDeProductos(PropiedadesExtraccion propiedades) {
    if (!propiedades.tieneClave()) {
      log.warn(
          "Extracción de productos SEMBRADA: sin ANTHROPIC_API_KEY solo se lee el precio y la"
              + " primera línea, y todo borrador sale con alertas.");
      return new ExtractorSembrado();
    }
    log.info(
        "Extracción de productos REAL con el modelo {} contra {}.",
        propiedades.modelo(),
        propiedades.urlBase());
    return new ExtractorClaude(
        URI.create(propiedades.urlBase()),
        propiedades.apiKey(),
        propiedades.modelo(),
        propiedades.maxTokens(),
        propiedades.timeout(),
        propiedades.intentos(),
        propiedades.esperaInicial(),
        RecursosDelExtractor.promptDeSistema(),
        RecursosDelExtractor.esquema());
  }

  @Bean
  public AgrupadorDePublicaciones agrupadorDePublicaciones(PropiedadesExtraccion propiedades) {
    return new AgrupadorDePublicaciones(propiedades.ventanaAgrupacion());
  }

  @Bean
  public ArmarPublicaciones armarPublicaciones(
      RepositorioLotesIngesta lotes,
      RepositorioMensajesProveedor mensajes,
      RepositorioPublicacionesProveedor publicaciones,
      AgrupadorDePublicaciones agrupador) {
    return new ArmarPublicaciones(lotes, mensajes, publicaciones, agrupador);
  }

  @Bean
  public ExtraerProductoDePublicacion extraerProductoDePublicacion(
      ExtractorDeProductos extractor, PropiedadesExtraccion propiedades) {
    return new ExtraerProductoDePublicacion(extractor, propiedades.umbralConfianza());
  }
}
