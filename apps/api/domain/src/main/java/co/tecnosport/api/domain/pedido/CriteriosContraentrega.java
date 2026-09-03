package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.Set;

/**
 * Configuración de negocio de contraentrega (docs/11-pagos-y-envios.md; {@code
 * CONTRAENTREGA_HABILITADA}/{@code CONTRAENTREGA_MONTO_MAXIMO}/{@code
 * CONTRAENTREGA_CATEGORIAS_EXCLUIDAS} en docs/07-infra-gcp.md). Sin valores por defecto a
 * propósito: la aplicación no arranca si falta alguno — regla dura del proyecto, nada de datos de
 * negocio inventados.
 */
public record CriteriosContraentrega(
    boolean habilitada, Dinero montoMaximo, Set<LineaCatalogo> categoriasExcluidas) {

  public CriteriosContraentrega {
    Objects.requireNonNull(montoMaximo, "El monto máximo no puede ser nulo.");
    categoriasExcluidas = Set.copyOf(Objects.requireNonNullElse(categoriasExcluidas, Set.of()));
  }
}
