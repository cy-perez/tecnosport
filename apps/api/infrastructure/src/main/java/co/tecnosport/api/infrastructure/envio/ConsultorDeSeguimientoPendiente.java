package co.tecnosport.api.infrastructure.envio;

import co.tecnosport.api.application.envio.AplicarEventoDeEnvioComando;
import co.tecnosport.api.application.envio.ConsultorDeSeguimiento;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * No sabe consultar nada todavía, y devuelve lista vacía en vez de lanzar: para la conciliación eso
 * significa "sin novedad", que es lo mismo que dice cuando el proveedor está caído. La tarea corre,
 * no rompe nada y registra que no encontró eventos.
 *
 * <p>Lo que falta: comprobar la ruta que anota adr/0022 —{@code GET
 * /shipments/tracking/{guia}/{transportadora}}— y la forma de su respuesta. Hace falta una guía
 * emitida para verlo, y la cuenta de sandbox no tiene créditos (docs/13-skydropx-capacidades.md,
 * sección 6).
 */
@Component
final class ConsultorDeSeguimientoPendiente implements ConsultorDeSeguimiento {

  @Override
  public List<AplicarEventoDeEnvioComando> consultar(String transportadora, String guia) {
    return List.of();
  }
}
