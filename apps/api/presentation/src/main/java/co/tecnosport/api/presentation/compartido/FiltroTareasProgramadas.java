package co.tecnosport.api.presentation.compartido;

import co.tecnosport.api.application.compartido.VerificadorDeTareasProgramadas;

/**
 * Autentica a Cloud Scheduler en {@code /api/v1/internal/tareas/} y le da el rol {@value #ROL}
 * (ADR-0104). El mecanismo está en {@link FiltroDeLlamadaInterna}.
 */
public class FiltroTareasProgramadas extends FiltroDeLlamadaInterna {

  public static final String ROL = "TAREAS_PROGRAMADAS";
  public static final String RUTA = "/api/v1/internal/tareas/";

  public FiltroTareasProgramadas(VerificadorDeTareasProgramadas verificador) {
    super(RUTA, ROL, "cloud-scheduler", verificador::esDelProgramador);
  }
}
