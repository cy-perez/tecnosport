package co.tecnosport.api.presentation.compartido;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.compartido.BitacoraDeTareas;
import co.tecnosport.api.application.compartido.EjecutarTareasVencidas;
import co.tecnosport.api.application.compartido.TareaProgramada;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Lo que Cloud Scheduler recibe por cada tick: {@code 200} con el resumen, también cuando una tarea
 * falla. La autenticación es de la cadena de seguridad (bootstrap) y se prueba con {@code
 * FiltroTareasProgramadasTest} y {@code CadenaDeSeguridadTest}.
 */
@WebMvcTest(TareasInternasControlador.class)
@Import({TareasInternasControladorTest.Configuracion.class, ManejadorDeErrores.class})
class TareasInternasControladorTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void unaTareaQueFallaNoConvierteElTickEnUnError() throws Exception {
    mockMvc
        .perform(post("/api/v1/internal/tareas/tick"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.corridas[0]").value("buena"))
        .andExpect(jsonPath("$.fallidas[0].tarea").value("rota"))
        .andExpect(
            jsonPath("$.fallidas[0].motivo")
                .value("java.lang.IllegalStateException: Skydropx no responde"));
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    EjecutarTareasVencidas ejecutarTareasVencidas() {
      BitacoraDeTareas siempreLeToca =
          new BitacoraDeTareas() {
            @Override
            public boolean tomar(
                String nombre, Duration intervalo, Duration arriendo, Instant ahora) {
              return true;
            }

            @Override
            public void terminar(String nombre, Instant terminadaEn, String fallo) {}
          };
      return new EjecutarTareasVencidas(
          List.of(tarea("buena", () -> {}), tarea("rota", Configuracion::fallar)),
          siempreLeToca,
          Instant::now,
          Duration.ofMinutes(30));
    }

    private static void fallar() {
      throw new IllegalStateException("Skydropx no responde");
    }

    private static TareaProgramada tarea(String nombre, Runnable trabajo) {
      return new TareaProgramada() {
        @Override
        public String nombre() {
          return nombre;
        }

        @Override
        public Duration intervalo() {
          return Duration.ofMinutes(1);
        }

        @Override
        public void ejecutar() {
          trabajo.run();
        }
      };
    }
  }
}
