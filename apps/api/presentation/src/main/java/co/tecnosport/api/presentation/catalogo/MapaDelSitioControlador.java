package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarMapaDelSitio;
import co.tecnosport.api.application.catalogo.MapaDelSitio;
import co.tecnosport.api.presentation.catalogo.dto.MapaDelSitioRespuesta;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuelga de {@code /api/v1/mapa-del-sitio} y no de {@code /api/v1/productos/mapa-del-sitio}: allí
 * chocaría con el {@code @GetMapping("/{slug}")} de {@link ProductoControlador}. Spring resolvería
 * bien la ruta literal por ser más específica, pero le robaría al catálogo un slug válido para
 * siempre, y el día que alguien publique un producto llamado así el fallo sería incomprensible.
 *
 * <p>Público, como el resto del catálogo: la seguridad solo exige rol en {@code /api/v1/admin/**},
 * y esto no expone nada que el sitemap no vaya a publicar.
 */
@RestController
@RequestMapping("/api/v1/mapa-del-sitio")
public class MapaDelSitioControlador {

  private static final Logger log = LoggerFactory.getLogger(MapaDelSitioControlador.class);

  private final ListarMapaDelSitio listarMapaDelSitio;

  public MapaDelSitioControlador(ListarMapaDelSitio listarMapaDelSitio) {
    this.listarMapaDelSitio = Objects.requireNonNull(listarMapaDelSitio);
  }

  /**
   * El aviso se registra aquí y no en el caso de uso porque {@code application} es framework-free y
   * no tiene con qué registrar. Es {@code warn} y no {@code error}: la respuesta es correcta y el
   * sitemap sirve; lo que pasa es que el catálogo creció más allá de lo que cabe en un archivo y
   * hay trabajo pendiente —partirlo y publicar un índice— que nadie va a ver venir de otra forma.
   */
  @GetMapping
  public MapaDelSitioRespuesta ver() {
    MapaDelSitio mapa = listarMapaDelSitio.ejecutar();
    if (mapa.truncado()) {
      log.warn(
          "El mapa del sitio se cortó en {} URL: el catálogo ya no cabe en un solo sitemap y hay"
              + " productos publicados que ningún buscador va a encontrar por aquí. Toca partirlo"
              + " en varios archivos con un índice.",
          mapa.entradas().size());
    }
    return new MapaDelSitioRespuesta(
        mapa.entradas().stream()
            .map(
                entrada ->
                    new MapaDelSitioRespuesta.Producto(
                        entrada.slug().valor(), entrada.actualizadoEn()))
            .toList());
  }
}
