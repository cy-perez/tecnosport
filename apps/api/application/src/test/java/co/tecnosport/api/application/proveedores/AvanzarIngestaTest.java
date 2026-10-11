package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnTransaccionPropiaFalsa;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ProcesarLoteDeIngesta.Resultado;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.FalloDeLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MotivoDeFalloDeLote;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Lo que decide una tarea después de su tramo: qué encolar, y que lata mientras trabaja. */
class AvanzarIngestaTest {

  private static final Instant AHORA = Instant.parse("2026-10-11T15:00:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final List<UUID> encolados = new ArrayList<>();
  private final List<String> latidos = new ArrayList<>();

  private final EjecutorDeIngestas cola =
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

  private final MarcapasosDeIngesta marcapasos =
      loteId -> {
        latidos.add("empieza " + loteId);
        return () -> latidos.add("termina " + loteId);
      };

  private LoteIngesta subido(int segundosDespues) {
    LoteIngesta lote =
        LoteIngesta.recibirExportacion(
            PROVEEDOR, "p/" + UUID.randomUUID() + ".zip", AHORA.plusSeconds(segundosDespues));
    lotes.guardar(lote);
    return lote;
  }

  /**
   * Un {@link ProcesarLoteDeIngesta} de verdad sería un montaje enorme para lo que aquí importa,
   * que es lo que pasa después del tramo: este hace con el lote lo que diga la prueba, y anota que
   * trabajó para comprobar que lo hizo latiendo.
   */
  private AvanzarIngesta avanzarQue(Function<LoteIngesta, Resultado> tramo) {
    return new AvanzarIngesta(
        id -> {
          latidos.add("trabaja");
          LoteIngesta lote = lotes.buscarPorId(id).orElseThrow();
          return new ProcesarLoteDeIngesta.Tramo(lote, tramo.apply(lote));
        },
        new EncolarIngesta(cola, lotes, new EnTransaccionPropiaFalsa(), new RelojFalso(AHORA)),
        marcapasos);
  }

  @Test
  void conTrabajoPendienteEncolaSuTramoSiguienteYLatioMientrasTrabajaba() {
    LoteIngesta lote = subido(0);

    avanzarQue(
            l -> {
              l.iniciar(AHORA);
              l.soltarParaSeguir(ResumenIngesta.vacio(), AHORA);
              return Resultado.SIGUE;
            })
        .ejecutar(lote.id());

    assertEquals(List.of(lote.id()), encolados);
    assertEquals(
        List.of("empieza " + lote.id(), "trabaja", "termina " + lote.id()),
        latidos,
        "el latido envuelve el tramo");
  }

  /** Al terminar suelta al proveedor: el lote que lo esperaba se encola. */
  @Test
  void alTerminarEncolaAlQueEsperabaDelMismoProveedor() {
    LoteIngesta primero = subido(0);
    LoteIngesta segundo = subido(1);

    avanzarQue(
            l -> {
              l.iniciar(AHORA);
              l.terminar(ResumenIngesta.vacio(), AHORA);
              return Resultado.SOLTADO;
            })
        .ejecutar(primero.id());

    assertEquals(List.of(segundo.id()), encolados);
  }

  /** En pausa sigue reteniendo a los del mismo proveedor: no se encola el que espera. */
  @Test
  void alPausarNoEncolaAlQueEsperaba() {
    LoteIngesta primero = subido(0);
    subido(1);

    avanzarQue(
            l -> {
              l.iniciar(AHORA);
              l.pausar();
              return Resultado.SOLTADO;
            })
        .ejecutar(primero.id());

    assertEquals(List.of(), encolados);
  }

  /**
   * Un tramo repetido que encuentra el lote ya cerrado no encola al siguiente: cada repetición
   * abriría otra cadena de tareas para él.
   */
  @Test
  void siNoLoTomoNoEncolaNada() {
    LoteIngesta primero = subido(0);
    subido(1);
    primero.iniciar(AHORA);
    primero.terminar(ResumenIngesta.vacio(), AHORA);

    avanzarQue(l -> Resultado.NADA).ejecutar(primero.id());

    assertEquals(List.of(), encolados);
  }

  /** Lo tiene otro vivo: sale para que la cola reintente más tarde. */
  @Test
  void siLoTieneOtroVivoLoDiceParaQueLaColaReintente() {
    LoteIngesta lote = subido(0);

    assertThrows(
        LoteOcupadoException.class, () -> avanzarQue(l -> Resultado.OCUPADO).ejecutar(lote.id()));
    assertEquals(List.of(), encolados);
  }

  /**
   * Si el tramo falla y el lote quedó cerrado, el que esperaba no tiene por qué seguir esperando.
   */
  @Test
  void siElTramoFallaYCierraElLoteEncolaAlQueEsperaba() {
    LoteIngesta primero = subido(0);
    LoteIngesta segundo = subido(1);

    TramoFallidoException error =
        assertThrows(
            TramoFallidoException.class,
            () ->
                avanzarQue(
                        l -> {
                          l.iniciar(AHORA);
                          l.fallar(FalloDeLote.de(MotivoDeFalloDeLote.ERROR_INESPERADO), AHORA);
                          throw new TramoFallidoException(
                              new IllegalStateException("se cayó"), false);
                        })
                    .ejecutar(primero.id()));

    assertEquals("se cayó", error.getCause().getMessage());
    assertEquals(List.of(segundo.id()), encolados);
    assertTrue(latidos.contains("termina " + primero.id()), "el latido se detiene igual");
  }

  /**
   * Si el lote volvió a la cola para reintentar, sigue ocupando al proveedor: nadie más se encola.
   */
  @Test
  void siElTramoFallaYElLoteVuelveALaColaNoEncolaANadie() {
    LoteIngesta primero = subido(0);
    subido(1);

    assertThrows(
        TramoFallidoException.class,
        () ->
            avanzarQue(
                    l -> {
                      throw new TramoFallidoException(new IllegalStateException("la base"), true);
                    })
                .ejecutar(primero.id()));

    assertEquals(List.of(), encolados);
    assertEquals(EstadoLote.RECIBIDO, primero.estado());
  }
}
