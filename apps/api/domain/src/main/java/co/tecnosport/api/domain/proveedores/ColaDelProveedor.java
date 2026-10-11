package co.tecnosport.api.domain.proveedores;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Los lotes de un mismo proveedor se procesan de a uno y en el orden en que se subieron.
 *
 * <p>Dos lotes del mismo proveedor a la vez podrían registrar el mismo mensaje dos veces, y el
 * orden importa: el chat de caballero de Meraki tiene que leerse antes que el general, que descarta
 * lo que aquel ya trajo (ADR-0067). Mientras la cola vivió en un hilo, el hilo único lo
 * garantizaba; Cloud Tasks no garantiza el orden de entrega (ADR-0080), así que lo garantiza esta
 * regla.
 *
 * <p>El orden es la fecha de subida y, a igual fecha, el id: los dos lotes de un zip de dos chats
 * pueden nacer en el mismo milisegundo, y sin un orden total los dos esperarían al otro o ninguno.
 */
public final class ColaDelProveedor {

  private static final Comparator<LoteIngesta> ORDEN =
      Comparator.comparing(LoteIngesta::creadoEn).thenComparing(LoteIngesta::id);

  private ColaDelProveedor() {}

  /** Hay uno anterior del mismo proveedor que todavía ocupa la cola: este tiene que esperar. */
  public static boolean tieneQueEsperar(LoteIngesta lote, Collection<LoteIngesta> abiertos) {
    Objects.requireNonNull(lote);
    return abiertos.stream()
        .filter(otro -> !otro.id().equals(lote.id()))
        .filter(otro -> otro.proveedorId().equals(lote.proveedorId()))
        .filter(LoteIngesta::ocupaAlProveedor)
        .anyMatch(otro -> ORDEN.compare(otro, lote) < 0);
  }

  /** El que sigue en la cola del proveedor: el más antiguo que espera a que alguien lo tome. */
  public static Optional<UUID> siguiente(UUID proveedorId, Collection<LoteIngesta> abiertos) {
    Objects.requireNonNull(proveedorId);
    return abiertos.stream()
        .filter(lote -> lote.proveedorId().equals(proveedorId))
        .filter(lote -> lote.estado() == EstadoLote.RECIBIDO)
        .min(ORDEN)
        .map(LoteIngesta::id);
  }
}
