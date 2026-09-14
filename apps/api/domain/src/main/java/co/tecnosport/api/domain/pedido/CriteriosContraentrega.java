package co.tecnosport.api.domain.pedido;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.Set;

/**
 * Configuración de negocio de contraentrega (docs/11-pagos-y-envios.md; {@code
 * CONTRAENTREGA_HABILITADA}/{@code CONTRAENTREGA_MONTO_MINIMO}/{@code
 * CONTRAENTREGA_MONTO_MAXIMO}/{@code CONTRAENTREGA_CATEGORIAS_EXCLUIDAS} en docs/07-infra-gcp.md).
 * Sin valores por defecto a propósito: la aplicación no arranca si falta alguno — regla dura del
 * proyecto, nada de datos de negocio inventados.
 *
 * <p><b>El mínimo llegó después que el máximo</b>, y no por olvido: durante toda la fase hubo un
 * tope y ningún piso, porque el tope protege del riesgo obvio —despachar mercancía cara contra la
 * promesa de que alguien pague al recibirla— y el piso protege de otro que no se ve, que es que la
 * transportadora no recauda por debajo de cierto valor. Los dos son límites de quien recauda, no
 * preferencias del negocio, y los dos se comparan contra el <b>total del pedido</b>: es la cifra
 * que la transportadora cobra en la puerta, con el flete dentro.
 */
public record CriteriosContraentrega(
    boolean habilitada,
    Dinero montoMinimo,
    Dinero montoMaximo,
    Set<LineaCatalogo> categoriasExcluidas) {

  public CriteriosContraentrega {
    Objects.requireNonNull(montoMinimo, "El monto mínimo no puede ser nulo.");
    Objects.requireNonNull(montoMaximo, "El monto máximo no puede ser nulo.");
    if (montoMinimo.valor().compareTo(montoMaximo.valor()) > 0) {
      throw new IllegalArgumentException(
          "El monto mínimo de contraentrega ("
              + montoMinimo
              + ") no puede superar al máximo ("
              + montoMaximo
              + "): así configurado, ningún pedido calificaría nunca y nadie sabría por qué.");
    }
    categoriasExcluidas = Set.copyOf(Objects.requireNonNullElse(categoriasExcluidas, Set.of()));
  }
}
