package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.ListarVariantesSinMargen;
import co.tecnosport.api.presentation.proveedores.dto.VariantesSinMargenRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El aviso del panel de lo que el costo del proveedor dejó sin margen (27 de octubre de 2026). Al
 * lado de {@code /admin/variantes/sin-medir}, que es el otro vigilante del panel, pero aparte de
 * {@code AdminVarianteControlador}: la marca la ponen la lista de tecnología y la ingesta, no el
 * catálogo.
 */
@RestController
public class AdminVariantesSinMargenControlador {

  private final ListarVariantesSinMargen listarVariantesSinMargen;

  public AdminVariantesSinMargenControlador(ListarVariantesSinMargen listarVariantesSinMargen) {
    this.listarVariantesSinMargen = Objects.requireNonNull(listarVariantesSinMargen);
  }

  /** Sin paginar: es una lista de pendientes que tiene que llegar a cero. */
  @GetMapping("/api/v1/admin/variantes/sin-margen")
  public VariantesSinMargenRespuesta sinMargen() {
    return VariantesSinMargenRespuesta.de(listarVariantesSinMargen.ejecutar());
  }
}
