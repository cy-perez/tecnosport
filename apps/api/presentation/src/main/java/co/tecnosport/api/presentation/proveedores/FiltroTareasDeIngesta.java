package co.tecnosport.api.presentation.proveedores;

import co.tecnosport.api.application.proveedores.VerificadorDeTareasDeIngesta;
import co.tecnosport.api.presentation.compartido.FiltroDeLlamadaInterna;

/**
 * Autentica a Cloud Tasks en {@code /api/v1/internal/ingestas/} y le da el rol {@value #ROL}
 * (ADR-0080). El mecanismo está en {@link FiltroDeLlamadaInterna}.
 *
 * <p>Hasta ADR-0104 la ruta era todo {@code /api/v1/internal/}. Se acotó cuando llegó el tick: con
 * la ruta entera, el token de la cola también habría abierto las tareas programadas.
 */
public class FiltroTareasDeIngesta extends FiltroDeLlamadaInterna {

  public static final String ROL = "TAREAS_DE_INGESTA";
  public static final String RUTA = "/api/v1/internal/ingestas/";

  public FiltroTareasDeIngesta(VerificadorDeTareasDeIngesta verificador) {
    super(RUTA, ROL, "cloud-tasks", verificador::esDeLaCola);
  }
}
