package co.tecnosport.api.presentation.proveedores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tecnosport.api.application.proveedores.AvanzarIngesta;
import co.tecnosport.api.application.proveedores.EjecutorDeIngestas;
import co.tecnosport.api.application.proveedores.EncolarIngesta;
import co.tecnosport.api.application.proveedores.LoteNoEncontradoException;
import co.tecnosport.api.application.proveedores.ProcesarLoteDeIngesta;
import co.tecnosport.api.application.proveedores.TramoFallidoException;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.presentation.ManejadorDeErrores;
import co.tecnosport.api.presentation.proveedores.AdminIngestaControladorTest.RepositorioLotesDoble;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Lo que Cloud Tasks recibe por cada tramo: {@code 204} cuando terminó, o cuando no hay nada que
 * reintentar; un error cuando algo falló, para que la cola lo reintente. La autenticación es de la
 * cadena de seguridad (bootstrap) y se prueba con {@code FiltroTareasDeIngestaTest}.
 */
@WebMvcTest(IngestaInternaControlador.class)
@Import({IngestaInternaControladorTest.Configuracion.class, ManejadorDeErrores.class})
class IngestaInternaControladorTest {

  private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private TramoGuionado tramo;
  @Autowired private RepositorioLotesDoble lotes;

  @BeforeEach
  void limpiar() {
    lotes.porId.clear();
    tramo.accion =
        id ->
            new ProcesarLoteDeIngesta.Tramo(
                lotes.porId.get(id), ProcesarLoteDeIngesta.Resultado.NADA);
  }

  private LoteIngesta subido() {
    LoteIngesta lote = LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/x.zip", AHORA);
    lotes.porId.put(lote.id(), lote);
    return lote;
  }

  @Test
  void unTramoQueTerminaResponde204() throws Exception {
    LoteIngesta lote = subido();

    mockMvc
        .perform(post("/api/v1/internal/ingestas/{id}/tramo", lote.id()))
        .andExpect(status().isNoContent());
  }

  /** Se eliminó mientras esperaba: reintentar no lo va a traer de vuelta. */
  @Test
  void unLoteQueYaNoExisteTambienResponde204() throws Exception {
    tramo.accion =
        id -> {
          throw new LoteNoEncontradoException(id);
        };

    mockMvc
        .perform(post("/api/v1/internal/ingestas/{id}/tramo", UUID.randomUUID()))
        .andExpect(status().isNoContent());
  }

  /** El lote volvió a la cola: 503 para que la cola reintente, y el reintento lo retoma. */
  @Test
  void unFalloQueDevuelveElLoteALaColaResponde503() throws Exception {
    LoteIngesta lote = subido();
    tramo.accion =
        id -> {
          throw new TramoFallidoException(new IllegalStateException("la base no responde"), true);
        };

    mockMvc
        .perform(post("/api/v1/internal/ingestas/{id}/tramo", lote.id()))
        .andExpect(status().isServiceUnavailable());
  }

  /** El lote quedó cerrado: reintentar no cambiaría nada, y la cola no debe hacerlo. */
  @Test
  void unFalloQueCierraElLoteResponde204() throws Exception {
    LoteIngesta lote = subido();
    tramo.accion =
        id -> {
          throw new TramoFallidoException(new IllegalStateException("zip ilegible"), false);
        };

    mockMvc
        .perform(post("/api/v1/internal/ingestas/{id}/tramo", lote.id()))
        .andExpect(status().isNoContent());
  }

  /** Lo tiene otro trabajador vivo: 503, y si ese murió, el reintento lo retomará. */
  @Test
  void unLoteDeOtroTrabajadorVivoResponde503() throws Exception {
    LoteIngesta lote = subido();
    tramo.accion =
        id -> new ProcesarLoteDeIngesta.Tramo(lote, ProcesarLoteDeIngesta.Resultado.OCUPADO);

    mockMvc
        .perform(post("/api/v1/internal/ingestas/{id}/tramo", lote.id()))
        .andExpect(status().isServiceUnavailable());
  }

  /** Lo que no es del tramo —encolar el siguiente, por ejemplo— sale como error y se reintenta. */
  @Test
  void otroFalloRespondeConErrorParaQueLaColaReintente() throws Exception {
    LoteIngesta lote = subido();
    tramo.accion =
        id -> {
          throw new IllegalStateException("la base no responde");
        };

    mockMvc
        .perform(post("/api/v1/internal/ingestas/{id}/tramo", lote.id()))
        .andExpect(status().is5xxServerError());
  }

  /** El tramo que hace la prueba, en lugar del lote entero. */
  static final class TramoGuionado implements Function<UUID, ProcesarLoteDeIngesta.Tramo> {
    Function<UUID, ProcesarLoteDeIngesta.Tramo> accion;

    @Override
    public ProcesarLoteDeIngesta.Tramo apply(UUID id) {
      return accion.apply(id);
    }
  }

  @TestConfiguration
  static class Configuracion {

    @Bean
    RepositorioLotesDoble repositorioLotes() {
      return new RepositorioLotesDoble();
    }

    @Bean
    TramoGuionado tramo() {
      return new TramoGuionado();
    }

    @Bean
    AvanzarIngesta avanzarIngesta(TramoGuionado tramo, RepositorioLotesDoble lotes) {
      List<UUID> encolados = new ArrayList<>();
      EjecutorDeIngestas cola =
          new EjecutorDeIngestas() {
            @Override
            public void encolar(UUID loteId) {
              encolados.add(loteId);
            }

            @Override
            public boolean sobreviveAUnReinicio() {
              return true;
            }
          };
      return new AvanzarIngesta(
          tramo,
          new EncolarIngesta(
              cola,
              lotes,
              new co.tecnosport.api.application.compartido.EnTransaccionPropia() {
                @Override
                public <T> T ejecutar(java.util.function.Supplier<T> trabajo) {
                  return trabajo.get();
                }
              },
              () -> AHORA),
          loteId -> () -> {});
    }
  }
}
