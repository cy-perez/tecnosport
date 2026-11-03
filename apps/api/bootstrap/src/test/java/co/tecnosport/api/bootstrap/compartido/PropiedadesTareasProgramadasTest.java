package co.tecnosport.api.bootstrap.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tecnosport.api.bootstrap.compartido.PropiedadesTareasProgramadas.Disparador;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PropiedadesTareasProgramadasTest {

  private static final String LLAVES = "https://www.googleapis.com/oauth2/v3/certs";
  private static final Duration ARRIENDO = Duration.ofMinutes(35);

  @Test
  void elInternoNoPideNadaMas() {
    PropiedadesTareasProgramadas propiedades =
        new PropiedadesTareasProgramadas(Disparador.INTERNO, "", "", LLAVES, ARRIENDO);

    assertThat(propiedades.externo()).isFalse();
  }

  /** Sin cuenta, cada tick rebotaría con un 401 y ninguna tarea correría, sin que nada lo avise. */
  @Test
  void elExternoSinCuentaNoArranca() {
    assertThatThrownBy(
            () ->
                new PropiedadesTareasProgramadas(
                    Disparador.EXTERNO, " ", "https://api.run.app", LLAVES, ARRIENDO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cuenta-de-servicio");
  }

  @Test
  void elExternoSinAudienciaNoArranca() {
    assertThatThrownBy(
            () ->
                new PropiedadesTareasProgramadas(
                    Disparador.EXTERNO, "tareas@p.iam.gserviceaccount.com", null, LLAVES, ARRIENDO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audiencia");
  }

  @Test
  void elExternoCompletoArranca() {
    PropiedadesTareasProgramadas propiedades =
        new PropiedadesTareasProgramadas(
            Disparador.EXTERNO,
            "tareas@p.iam.gserviceaccount.com",
            "https://api.run.app",
            LLAVES,
            ARRIENDO);

    assertThat(propiedades.externo()).isTrue();
  }

  @Test
  void sinDisparadorOConArriendoCeroNoArranca() {
    assertThatThrownBy(() -> new PropiedadesTareasProgramadas(null, "", "", LLAVES, ARRIENDO))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new PropiedadesTareasProgramadas(Disparador.INTERNO, "", "", LLAVES, Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
  }
}
