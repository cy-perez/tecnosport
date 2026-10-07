package co.tecnosport.api.bootstrap.difusion;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.RepositorioProductos;
import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.difusion.AjustadorDeImagenes;
import co.tecnosport.api.application.difusion.ArmadorDePieDeFoto;
import co.tecnosport.api.application.difusion.DifundirProducto;
import co.tecnosport.api.application.difusion.PublicadorEnRedSocial;
import co.tecnosport.api.application.difusion.RepositorioPublicaciones;
import co.tecnosport.api.domain.compartido.Hashtag;
import co.tecnosport.api.infrastructure.difusion.AjustadorDeImagenesJava2D;
import co.tecnosport.api.infrastructure.difusion.MetaGraphClient;
import co.tecnosport.api.infrastructure.difusion.siembra.PublicadorSembrado;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mismo patrón que {@code ConfiguracionEnvio} y {@code ConfiguracionCatalogo}. */
@Configuration
@EnableConfigurationProperties(PropiedadesMeta.class)
public class ConfiguracionDifusion {

  private static final Logger log = LoggerFactory.getLogger(ConfiguracionDifusion.class);

  /**
   * El publicador: el de verdad solo si alguien lo encendió a propósito.
   *
   * <p>Se decide con una propiedad y no con {@code @Profile}, al revés que en envíos, y el porqué
   * está en {@link PropiedadesMeta}: aquí no hay ambiente de pruebas al que equivocarse apunte. El
   * aviso al arrancar no es decorativo — es la forma de que, mirando el registro, se sepa en qué
   * modo quedó una instancia sin tener que deducirlo de las variables de entorno.
   */
  @Bean
  public PublicadorEnRedSocial publicadorEnRedSocial(PropiedadesMeta propiedades) {
    if (!propiedades.publicarDeVerdad()) {
      log.warn(
          "Difusión en redes SIMULADA: nada saldrá a Facebook ni a Instagram. Para publicar de"
              + " verdad hay que poner META_PUBLICAR_DE_VERDAD=true.");
      return new PublicadorSembrado();
    }
    log.info(
        "Difusión en redes REAL contra la página {} y la cuenta de Instagram {}.",
        propiedades.pageId(),
        propiedades.igUserId());
    return new MetaGraphClient(
        URI.create(propiedades.urlBase()),
        propiedades.pageId(),
        propiedades.igUserId(),
        propiedades.token(),
        Duration.ofSeconds(propiedades.timeoutSegundos()),
        propiedades.sondeosDelContenedor(),
        Duration.ofSeconds(propiedades.esperaEntreSondeosSegundos()));
  }

  /**
   * Las etiquetas que van en todo lo que se publique llegan como una cadena separada por espacios
   * —{@code META_HASHTAGS_DE_MARCA="#TecnoSport #Medellín"}— y no como una lista de YAML, para que
   * se puedan cambiar desde una variable de entorno sin tocar un archivo. Las de cada categoría no
   * están aquí: esas viven en la categoría y las edita el panel.
   */
  @Bean
  public ArmadorDePieDeFoto armadorDePieDeFoto(PropiedadesMeta propiedades) {
    List<Hashtag> deMarca =
        Arrays.stream(propiedades.hashtagsDeMarca().split("[\\s,]+"))
            .filter(t -> !t.isBlank())
            .map(Hashtag::new)
            .toList();
    return new ArmadorDePieDeFoto(propiedades.urlBaseDelSitio(), deMarca);
  }

  /**
   * Encaja las fotos en la proporción que pida la red, con {@code ImageIO} y {@code Graphics2D} del
   * JDK. No hay interruptor para apagarlo: una foto que la red no admite se queda fuera del
   * carrusel, y eso no se arregla solo.
   */
  @Bean
  public AjustadorDeImagenes ajustadorDeImagenes(AlmacenDeImagenes almacenDeImagenes) {
    return new AjustadorDeImagenesJava2D(almacenDeImagenes);
  }

  @Bean
  public DifundirProducto difundirProducto(
      RepositorioProductos repositorioProductos,
      RepositorioPublicaciones repositorioPublicaciones,
      PublicadorEnRedSocial publicador,
      AjustadorDeImagenes ajustador,
      ArmadorDePieDeFoto armador,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    return new DifundirProducto(
        repositorioProductos,
        repositorioPublicaciones,
        publicador,
        ajustador,
        armador,
        enTransaccionPropia,
        reloj);
  }
}
