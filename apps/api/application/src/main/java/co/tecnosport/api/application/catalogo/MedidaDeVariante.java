package co.tecnosport.api.application.catalogo;

import co.tecnosport.api.domain.catalogo.EstadoProducto;
import co.tecnosport.api.domain.catalogo.Paquete;
import java.util.Optional;
import java.util.UUID;

/**
 * Una variante activa con la medida de su paquete, que puede no existir todavía.
 *
 * <p>Reemplaza a {@code VarianteSinMedir} como lo que trae la consulta: pedir "las que faltan" y
 * "todas con su medida" eran dos {@code select} gemelos sobre la misma tabla, y el segundo incluye
 * al primero. Quién falta por medir lo decide ahora {@link ListarVariantesSinMedir} filtrando, que
 * es donde una regla de negocio se puede probar — no un {@code where} que no mira ninguna prueba.
 */
public record MedidaDeVariante(
    UUID varianteId,
    UUID productoId,
    String nombreProducto,
    String sku,
    EstadoProducto estadoProducto,
    Paquete paquete) {

  /** Vacío cuando nadie la ha medido. Sin medidas no hay envío a domicilio (adr/0046). */
  public Optional<Paquete> medida() {
    return Optional.ofNullable(paquete);
  }

  public boolean sinMedir() {
    return paquete == null;
  }
}
