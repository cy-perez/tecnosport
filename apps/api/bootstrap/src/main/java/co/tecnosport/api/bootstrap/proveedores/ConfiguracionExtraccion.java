package co.tecnosport.api.bootstrap.proveedores;

import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import co.tecnosport.api.application.proveedores.ArmarPublicaciones;
import co.tecnosport.api.application.proveedores.ExtractorDeProductos;
import co.tecnosport.api.application.proveedores.ExtraerProductoDePublicacion;
import co.tecnosport.api.application.proveedores.LectorDeFotos;
import co.tecnosport.api.application.proveedores.RepositorioLotesIngesta;
import co.tecnosport.api.application.proveedores.RepositorioMensajesProveedor;
import co.tecnosport.api.application.proveedores.RepositorioProveedores;
import co.tecnosport.api.application.proveedores.RepositorioPublicacionesProveedor;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.infrastructure.proveedores.extraccion.ExtractorClaude;
import co.tecnosport.api.infrastructure.proveedores.extraccion.ExtractorSembrado;
import co.tecnosport.api.infrastructure.proveedores.extraccion.LectorDeFotosApagado;
import co.tecnosport.api.infrastructure.proveedores.extraccion.LectorDeFotosClaude;
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
@EnableConfigurationProperties({PropiedadesExtraccion.class, PropiedadesLecturaFotos.class})
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

  /** Como el extractor: el de verdad solo con clave y encendido, y el arranque dice cuál quedó. */
  @Bean
  public LectorDeFotos lectorDeFotos(
      PropiedadesExtraccion extraccion, PropiedadesLecturaFotos lectura) {
    if (!extraccion.tieneClave() || !lectura.habilitada()) {
      log.warn(
          "Lectura de fotos APAGADA: las fotos de una publicación con varios productos o de un"
              + " álbum quedan para todos sus borradores.");
      return new LectorDeFotosApagado();
    }
    log.info("Lectura de fotos REAL con el modelo {}.", lectura.modelo());
    return new LectorDeFotosClaude(
        URI.create(extraccion.urlBase()),
        extraccion.apiKey(),
        lectura.modelo(),
        lectura.maxTokens(),
        lectura.timeout(),
        extraccion.intentos(),
        extraccion.esperaInicial(),
        RecursosDelExtractor.promptDelLector(),
        RecursosDelExtractor.esquemaDelLector());
  }

  @Bean
  public AgrupadorDePublicaciones agrupadorDePublicaciones(PropiedadesExtraccion propiedades) {
    return new AgrupadorDePublicaciones(propiedades.ventanaAgrupacion());
  }

  @Bean
  public ArmarPublicaciones armarPublicaciones(
      RepositorioLotesIngesta lotes,
      RepositorioProveedores proveedores,
      RepositorioMensajesProveedor mensajes,
      RepositorioPublicacionesProveedor publicaciones,
      AgrupadorDePublicaciones agrupador) {
    return new ArmarPublicaciones(lotes, proveedores, mensajes, publicaciones, agrupador);
  }

  @Bean
  public ExtraerProductoDePublicacion extraerProductoDePublicacion(
      ExtractorDeProductos extractor,
      LectorDeFotos lector,
      AlmacenDeArchivosDeProveedor almacen,
      PropiedadesExtraccion propiedades) {
    return new ExtraerProductoDePublicacion(
        extractor, lector, almacen, propiedades.umbralConfianza());
  }
}
