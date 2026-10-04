package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.ListarPaletaDeColores;
import co.tecnosport.api.presentation.catalogo.dto.ColorDePaletaRespuesta;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La paleta de colores. Pública como {@code /atributos}: no es sensible, el panel la usa para
 * marcar el tono de cada foto y la vitrina para decir el nombre del color en inglés.
 */
@RestController
@RequestMapping("/api/v1/colores")
public class PaletaDeColoresControlador {

  private final ListarPaletaDeColores listarPaletaDeColores;

  public PaletaDeColoresControlador(ListarPaletaDeColores listarPaletaDeColores) {
    this.listarPaletaDeColores = Objects.requireNonNull(listarPaletaDeColores);
  }

  @GetMapping
  public List<ColorDePaletaRespuesta> listar() {
    return listarPaletaDeColores.ejecutar().stream()
        .map(
            c ->
                new ColorDePaletaRespuesta(
                    c.id(),
                    c.nombre(),
                    c.nombreEn(),
                    c.hex(),
                    c.patron() == null ? null : c.patron().name(),
                    c.coloresDelPatron()))
        .toList();
  }
}
