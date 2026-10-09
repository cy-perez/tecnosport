package co.tecnosport.api.application.proveedores.tecnologia;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Las listas que ya entraron, por proveedor. Mover inventario exige idempotencia (apps/api/
 * CLAUDE.md): repetir el POST de una lista —un reintento tras un timeout, un doble clic— volvía a
 * reponer lo que se acababa de vender, y reimportar una lista vieja deshacía la de hoy.
 */
public interface RepositorioListasDeTecnologia {

  /** La fecha de la lista más reciente que entró de ese proveedor. */
  Optional<LocalDate> fechaDeLaUltima(UUID proveedorId);

  /** Si una lista con exactamente ese contenido ya entró. */
  boolean yaEntro(UUID proveedorId, String huella);

  void registrar(UUID proveedorId, LocalDate fechaLista, String huella, Instant importadaEn);
}
