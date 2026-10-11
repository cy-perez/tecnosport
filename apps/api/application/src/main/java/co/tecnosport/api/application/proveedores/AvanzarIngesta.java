package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.proveedores.ColaDelProveedor;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Lo que hace una tarea de la cola: un tramo del lote, latiendo mientras trabaja, y lo que toca
 * encolar después (ADR-0080).
 *
 * <ul>
 *   <li>Si el lote volvió a la cola con trabajo pendiente, se encola su tramo siguiente.
 *   <li>Si este tramo lo soltó y dejó de ocupar a su proveedor —terminó, falló o se detuvo—, se
 *       encola el lote que lo esperaba: el más nuevo del mismo proveedor no empezó porque este iba
 *       primero ({@link ColaDelProveedor}), y su tarea terminó sin hacer nada. Solo este tramo, y
 *       no uno repetido que encuentra el lote ya cerrado: si no, cada repetición abriría otra
 *       cadena.
 *   <li>Si lo tiene otro trabajador que sigue latiendo, sale {@link LoteOcupadoException}: con
 *       Cloud Tasks la tarea responde con error y la cola la reintenta más tarde, cuando ese latido
 *       ya habrá vencido si el trabajador murió.
 * </ul>
 *
 * <p>Si el tramo falla, sale {@link TramoFallidoException}, que dice si tiene sentido reintentar:
 * con Cloud Tasks, la cola reintenta la tarea y el reintento toma el lote que volvió a la cola. Si
 * encolar falla, la excepción también sale y la cola reintenta igual. Lo que se escapa a eso —una
 * tarea que se perdió— lo recoge {@link RecuperarIngestasAbandonadas}.
 */
public final class AvanzarIngesta {

  private final Function<UUID, ProcesarLoteDeIngesta.Tramo> procesarTramo;
  private final EncolarIngesta encolar;
  private final MarcapasosDeIngesta marcapasos;

  /**
   * @param procesarTramo {@link ProcesarLoteDeIngesta#procesarTramo}; una función para que las
   *     pruebas de lo que pasa después del tramo no tengan que montar el lote entero
   */
  public AvanzarIngesta(
      Function<UUID, ProcesarLoteDeIngesta.Tramo> procesarTramo,
      EncolarIngesta encolar,
      MarcapasosDeIngesta marcapasos) {
    this.procesarTramo = Objects.requireNonNull(procesarTramo);
    this.encolar = Objects.requireNonNull(encolar);
    this.marcapasos = Objects.requireNonNull(marcapasos);
  }

  /**
   * @return el lote como quedó después del tramo
   * @throws LoteNoEncontradoException si el lote no existe: se eliminó mientras esperaba
   * @throws LoteOcupadoException si lo tiene otro trabajador vivo
   * @throws TramoFallidoException si el tramo falló
   */
  public LoteIngesta ejecutar(UUID loteId) {
    Objects.requireNonNull(loteId, "El id no puede ser nulo.");
    ProcesarLoteDeIngesta.Tramo tramo;
    try (MarcapasosDeIngesta.Latido latido = marcapasos.latirPor(loteId)) {
      tramo = procesarTramo.apply(loteId);
    } catch (TramoFallidoException e) {
      if (!e.reintentar()) {
        // El lote quedó cerrado: el que lo esperaba no tiene por qué esperar más.
        try {
          encolar.siguienteDelProveedorDe(loteId);
        } catch (RuntimeException secundaria) {
          e.addSuppressed(secundaria);
        }
      }
      throw e;
    }
    switch (tramo.resultado()) {
      case SIGUE -> encolar.encolar(loteId);
      case SOLTADO -> encolar.siguienteDelProveedorDe(loteId);
      case OCUPADO -> throw new LoteOcupadoException(loteId);
      case NADA -> {
        // Repetido, esperando a otro, o ya de otro: lo moverá quien lo tenga o quien lo suelte.
      }
    }
    return tramo.lote();
  }
}
