package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.AvanzarIngesta;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.application.proveedores.LoteOcupadoException;
import co.tecnosport.api.application.proveedores.TramoFallidoException;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que llama Cloud Tasks por cada tramo de un lote (ADR-0080). No es del panel ni del público: lo
 * protege {@link FiltroTareasDeIngesta}, y no sale en el contrato publicado.
 *
 * <p>La respuesta es lo que la cola entiende: {@code 2xx} se da por cumplida, cualquier otra cosa
 * se reintenta con espera creciente.
 *
 * <ul>
 *   <li>{@code 204} cuando el tramo terminó —también si el lote siguió y ya se encoló el tramo
 *       siguiente, o si el tramo no tenía nada que hacer—, cuando el lote ya no existe —se eliminó
 *       mientras esperaba— y cuando el tramo falló y el lote quedó cerrado: reintentar no cambiaría
 *       nada.
 *   <li>{@code 503} cuando el tramo falló y el lote volvió a la cola: el reintento lo retoma donde
 *       quedó. Y cuando lo tiene otro trabajador que sigue latiendo: si ese murió, para el
 *       reintento su latido ya habrá vencido y el tramo lo retomará.
 *   <li>Lo demás —encolar el tramo siguiente falló, por ejemplo— sale como error y se reintenta.
 * </ul>
 *
 * <p>La petición dura lo que dure el tramo, y eso es lo que hace funcionar la ingesta en Cloud Run:
 * mientras una petición está abierta, la instancia tiene CPU y no se apaga.
 */
@RestController
@RequestMapping("/api/v1/internal/ingestas")
public class IngestaInternaControlador {

  private static final Logger log = LoggerFactory.getLogger(IngestaInternaControlador.class);

  private final AvanzarIngesta avanzarIngesta;

  public IngestaInternaControlador(AvanzarIngesta avanzarIngesta) {
    this.avanzarIngesta = Objects.requireNonNull(avanzarIngesta);
  }

  @PostMapping("/{id}/tramo")
  public ResponseEntity<Void> tramo(@PathVariable UUID id) {
    try {
      LoteIngesta lote = avanzarIngesta.ejecutar(id);
      log.info(
          "Tramo del lote de ingesta {} del proveedor {}: {} {}",
          lote.id(),
          lote.proveedorId(),
          lote.estado(),
          lote.resumen().map(Object::toString).orElse(""));
      lote.fallo()
          .ifPresent(
              fallo ->
                  log.warn(
                      "El lote de ingesta {} quedó {} por {} ({})",
                      lote.id(),
                      lote.estado(),
                      fallo.motivo(),
                      fallo.datoOpcional().orElse("")));
    } catch (LoteNoEncontradoException e) {
      log.info("El lote de ingesta {} se eliminó mientras esperaba en la cola.", id);
    } catch (LoteOcupadoException e) {
      log.info("El lote de ingesta {} lo tiene otro trabajador; la cola reintentará.", id);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    } catch (TramoFallidoException e) {
      if (e.reintentar()) {
        log.warn("Tramo del lote de ingesta {} falló; vuelve a la cola", id, e.getCause());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
      }
      // El lote ya quedó en ERROR con su motivo para el panel; aquí va la traza.
      log.error("Tramo del lote de ingesta {} falló y el lote quedó cerrado", id, e.getCause());
    }
    return ResponseEntity.noContent().build();
  }
}
