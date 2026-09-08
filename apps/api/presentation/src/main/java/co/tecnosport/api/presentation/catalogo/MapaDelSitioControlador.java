package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarMapaDelSitio;
import co.tecnosport.api.presentation.catalogo.dto.MapaDelSitioRespuesta;
import java.util.Objects;
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

  private final ListarMapaDelSitio listarMapaDelSitio;

  public MapaDelSitioControlador(ListarMapaDelSitio listarMapaDelSitio) {
    this.listarMapaDelSitio = Objects.requireNonNull(listarMapaDelSitio);
  }

  @GetMapping
  public MapaDelSitioRespuesta ver() {
    return new MapaDelSitioRespuesta(
        listarMapaDelSitio.ejecutar().stream()
            .map(
                entrada ->
                    new MapaDelSitioRespuesta.Producto(
                        entrada.slug().valor(), entrada.actualizadoEn()))
            .toList());
  }
}
