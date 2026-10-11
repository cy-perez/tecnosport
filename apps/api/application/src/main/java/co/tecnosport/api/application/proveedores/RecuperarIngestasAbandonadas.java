package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.domain.proveedores.ColaDelProveedor;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que se quedó sin nadie que lo mueva: el lote cuyo trabajador dejó de latir, y —con la cola de
 * Cloud Tasks— el que lleva demasiado en la cola sin que su tarea llegue.
 *
 * <ul>
 *   <li><b>Abandonado</b> ({@link LoteIngesta#abandonado}): se recupera ({@link
 *       LoteIngesta#recuperar}). Vuelve a la cola para que otro tramo lo retome, hasta tres veces;
 *       el que se estaba deteniendo queda detenido. Uno con el latido al día es de una instancia
 *       viva, aunque no sea la que pregunta, y no se toca: tocarlo era lo que pasaba antes del 11
 *       de octubre de 2026.
 *   <li><b>Estancado en la cola</b> ({@link LoteIngesta#estancadoEnLaCola}): se vuelve a encolar,
 *       sin cambiarle nada, salvo que esté esperando a uno anterior de su proveedor. Es lo que
 *       recoge un tramo que no se pudo encolar o una tarea que la cola perdió.
 * </ul>
 *
 * <p>No encola: devuelve lo que hay que encolar, para que quien llama lo haga <b>después de
 * confirmar</b> la transacción que abre, en la que esto escribe.
 */
public final class RecuperarIngestasAbandonadas {

  private final RepositorioLotesIngesta repositorioLotes;
  private final Reloj reloj;
  private final Duration toleranciaDelLatido;
  private final Duration toleranciaEnLaCola;

  /**
   * @param toleranciaDelLatido cuánto puede pasar sin latir antes de darlo por abandonado; varias
   *     veces el intervalo del latido, para que una pausa del recolector o una escritura lenta no
   *     maten una ingesta viva
   * @param toleranciaEnLaCola cuánto puede esperar en la cola sin moverse antes de volver a
   *     encolarlo; nula para no hacerlo
   */
  public RecuperarIngestasAbandonadas(
      RepositorioLotesIngesta repositorioLotes,
      Reloj reloj,
      Duration toleranciaDelLatido,
      Duration toleranciaEnLaCola) {
    this.repositorioLotes = Objects.requireNonNull(repositorioLotes);
    this.reloj = Objects.requireNonNull(reloj);
    this.toleranciaDelLatido = exigirPositiva(toleranciaDelLatido);
    this.toleranciaEnLaCola =
        toleranciaEnLaCola == null ? null : exigirPositiva(toleranciaEnLaCola);
  }

  private static Duration exigirPositiva(Duration tolerancia) {
    Objects.requireNonNull(tolerancia);
    if (tolerancia.isNegative() || tolerancia.isZero()) {
      throw new IllegalArgumentException("Una tolerancia tiene que ser positiva.");
    }
    return tolerancia;
  }

  public Resultado ejecutar() {
    Set<UUID> porEncolar = new LinkedHashSet<>();
    int retomados = 0;
    int cerrados = 0;
    List<UUID> proveedoresLiberados = new ArrayList<>();
    List<LoteIngesta> abiertos = repositorioLotes.abiertos();
    for (LoteIngesta leido : abiertos) {
      if (leido.enManosDelTrabajador()) {
        // Se relee bloqueado: su trabajador puede estar soltándolo, o latiendo, en este momento.
        LoteIngesta lote = repositorioLotes.buscarPorIdParaActualizar(leido.id()).orElse(null);
        if (lote == null || !lote.abandonado(reloj.ahora(), toleranciaDelLatido)) {
          continue;
        }
        switch (lote.recuperar(reloj.ahora())) {
          case RETOMADO -> {
            porEncolar.add(lote.id());
            retomados++;
          }
          case DETENIDO, FALLIDO -> {
            proveedoresLiberados.add(lote.proveedorId());
            cerrados++;
          }
        }
        repositorioLotes.actualizar(lote);
      } else if (toleranciaEnLaCola != null
          && leido.estancadoEnLaCola(reloj.ahora(), toleranciaEnLaCola)
          && !ColaDelProveedor.tieneQueEsperar(leido, abiertos)) {
        // El que espera a uno anterior de su proveedor no está estancado: lo encola quien suelte
        // a ese. Encolarlo cada minuto solo abriría tareas que no hacen nada.
        porEncolar.add(leido.id());
      }
    }
    // El que esperaba a uno que se cerró aquí: su tarea terminó sin hacer nada y nadie más lo va a
    // encolar. Se mira después de cerrar, sobre la lista releída.
    if (!proveedoresLiberados.isEmpty()) {
      List<LoteIngesta> releidos = repositorioLotes.abiertos();
      for (UUID proveedorId : proveedoresLiberados) {
        ColaDelProveedor.siguiente(proveedorId, releidos).ifPresent(porEncolar::add);
      }
    }
    return new Resultado(List.copyOf(porEncolar), retomados, cerrados);
  }

  /**
   * @param porEncolar los lotes que hay que encolar después de confirmar
   * @param retomados los abandonados que volvieron a la cola
   * @param cerrados los abandonados que quedaron detenidos o en error
   */
  public record Resultado(List<UUID> porEncolar, int retomados, int cerrados) {}
}
