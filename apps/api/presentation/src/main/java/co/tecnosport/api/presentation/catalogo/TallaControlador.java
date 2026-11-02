package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarTallasDisponibles;
import co.tecnosport.api.application.catalogo.OpcionDeTalla;
import co.tecnosport.api.application.catalogo.TallasDisponibles;
import co.tecnosport.api.presentation.catalogo.dto.OpcionDeTallaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.TallasDisponiblesRespuesta;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las tallas de los filtros de la vitrina, pública como {@code /colores}.
 *
 * <p>En {@code /tallas} y no en {@code /productos/tallas}: ahí la ruta competiría con la ficha,
 * {@code /productos/{slug}}, y un producto con el slug «tallas» quedaría inalcanzable.
 */
@RestController
@RequestMapping("/api/v1/tallas")
public class TallaControlador {

  private final ListarTallasDisponibles listarTallasDisponibles;

  public TallaControlador(ListarTallasDisponibles listarTallasDisponibles) {
    this.listarTallasDisponibles = Objects.requireNonNull(listarTallasDisponibles);
  }

  @GetMapping
  public TallasDisponiblesRespuesta listar() {
    TallasDisponibles tallas = listarTallasDisponibles.ejecutar();
    return new TallasDisponiblesRespuesta(aRespuesta(tallas.ropa()), aRespuesta(tallas.calzado()));
  }

  private static List<OpcionDeTallaRespuesta> aRespuesta(List<OpcionDeTalla> opciones) {
    return opciones.stream()
        .map(opcion -> new OpcionDeTallaRespuesta(opcion.valor(), opcion.etiqueta()))
        .toList();
  }
}
