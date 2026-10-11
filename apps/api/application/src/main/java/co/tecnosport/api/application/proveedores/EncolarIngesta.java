package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.EnTransaccionPropia;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.ColaDelProveedor;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pone lotes en la cola de ingestas. Siempre fuera de una transacción y después de confirmar la que
 * escribió el lote: si el trabajador arranca antes, no encuentra la fila como quedó.
 *
 * <p>Las formas se distinguen por lo que hacen cuando la cola no acepta el lote:
 *
 * <ul>
 *   <li>{@link #ejecutarTodos}, la de una subida: cierra con su motivo el que falló y los que
 *       venían detrás en la misma subida, y relanza para que el panel lo diga. Un lote recién
 *       subido que se quedara en la cola sin tarea mostraría «en cola» sin que pasara nada; y de un
 *       zip de dos chats, el general no puede seguir solo sin el de caballero.
 *   <li>{@link #encolar(UUID)}, la del tramo siguiente, la de un lote reanudado y la del que se
 *       recupera: relanza sin tocar el lote, que ya tiene avance. Lo recoge la tarea periódica
 *       ({@link RecuperarIngestasAbandonadas}) cuando lleve un rato en la cola sin moverse.
 *   <li>{@link #siguienteDelProveedorDe(UUID)}: el lote que esperaba a este, si hay uno.
 * </ul>
 */
public final class EncolarIngesta {

  static final String MOTIVO_COLA_LLENA =
      "La cola de ingestas estaba llena. Vuelve a subir la exportación.";
  static final String MOTIVO_COLA_NO_DISPONIBLE =
      "No pudimos poner la ingesta en la cola. Vuelve a subir la exportación en unos minutos.";

  private final EjecutorDeIngestas ejecutor;
  private final RepositorioLotesIngesta repositorioLotes;
  private final EnTransaccionPropia enTransaccionPropia;
  private final Reloj reloj;

  public EncolarIngesta(
      EjecutorDeIngestas ejecutor,
      RepositorioLotesIngesta repositorioLotes,
      EnTransaccionPropia enTransaccionPropia,
      Reloj reloj) {
    this.ejecutor = Objects.requireNonNull(ejecutor);
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.enTransaccionPropia = Objects.requireNonNull(enTransaccionPropia);
    this.reloj = Objects.requireNonNull(reloj);
  }

  /**
   * Los lotes de una subida, en el orden en que se tienen que procesar.
   *
   * @throws ColaDeIngestasLlenaException o {@link ColaDeIngestasNoDisponibleException} si la cola
   *     no aceptó alguno; ese y los que venían detrás quedan cerrados con su motivo
   */
  public void ejecutarTodos(List<LoteIngesta> lotes) {
    for (int i = 0; i < lotes.size(); i++) {
      try {
        ejecutor.encolar(lotes.get(i).id());
      } catch (ColaDeIngestasLlenaException | ColaDeIngestasNoDisponibleException e) {
        String motivo =
            e instanceof ColaDeIngestasLlenaException
                ? MOTIVO_COLA_LLENA
                : MOTIVO_COLA_NO_DISPONIBLE;
        for (LoteIngesta sinTarea : lotes.subList(i, lotes.size())) {
          fallar(sinTarea.id(), motivo);
        }
        throw e;
      }
    }
  }

  public void encolar(UUID loteId) {
    ejecutor.encolar(Objects.requireNonNull(loteId, "El id no puede ser nulo."));
  }

  /**
   * Encola el lote del mismo proveedor que esperaba a este, si este ya no lo ocupa.
   *
   * @return el que encoló, si hubo uno
   */
  public Optional<UUID> siguienteDelProveedorDe(UUID loteId) {
    Optional<LoteIngesta> lote = repositorioLotes.buscarPorId(loteId);
    if (lote.isEmpty() || lote.get().ocupaAlProveedor()) {
      return Optional.empty();
    }
    Optional<UUID> siguiente =
        ColaDelProveedor.siguiente(lote.get().proveedorId(), repositorioLotes.abiertos());
    siguiente.ifPresent(ejecutor::encolar);
    return siguiente;
  }

  /** Sobre el lote releído: entre la subida y este fallo, el panel pudo detenerlo. */
  private void fallar(UUID loteId, String motivo) {
    enTransaccionPropia.ejecutar(
        () -> {
          repositorioLotes
              .buscarPorIdParaActualizar(loteId)
              .filter(l -> l.estado() == EstadoLote.RECIBIDO)
              .ifPresent(
                  l -> {
                    l.fallar(motivo, reloj.ahora());
                    repositorioLotes.actualizar(l);
                  });
          return null;
        });
  }
}
