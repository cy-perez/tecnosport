package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.compartido.EnTransaccionPropiaFalsa;
import co.tecnosport.api.application.compartido.RelojFalso;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.FuenteFija;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.proveedores.EstadoLote;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.ResumenIngesta;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcesarLoteDeIngestaTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final String ARCHIVO = "proveedores/x/exportaciones/a.zip";

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final EnTransaccionPropiaFalsa transacciones = new EnTransaccionPropiaFalsa();
  private final RegistrarMensajesDeProveedor registrar =
      new RegistrarMensajesDeProveedor(proveedores, lotes, mensajes, almacen);

  private LoteIngesta lote;

  @BeforeEach
  void unLoteEnLaCola() {
    Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);
    lote = LoteIngesta.recibirExportacion(proveedor.id(), ARCHIVO, T);
    lotes.guardar(lote);
  }

  private ProcesarLoteDeIngesta casoCon(FuenteFija fuente) {
    return new ProcesarLoteDeIngesta(
        lotes, fuente, registrar, transacciones, new RelojFalso(T.plusSeconds(5)));
  }

  @Test
  void leeElArchivoRegistraYCierraConElResumen() {
    FuenteFija fuente =
        new FuenteFija(
            List.of(
                MensajeCrudo.texto(T, ApoyoDeIngesta.REMITENTE, "Bolso 💰 53.000"),
                MensajeCrudo.imagen(
                    T.plusSeconds(10),
                    ApoyoDeIngesta.REMITENTE,
                    null,
                    ApoyoDeIngesta.foto("a.jpg")),
                MensajeCrudo.texto(T.plusSeconds(60), "Tecno Sport", "Dale")));

    LoteIngesta resultado = casoCon(fuente).ejecutar(lote.id());

    assertEquals(ARCHIVO, fuente.ultimaReferencia);
    assertEquals(EstadoLote.TERMINADO, resultado.estado());
    assertEquals(Optional.of(new ResumenIngesta(3, 1, 2, 0, 0, 0, 0, 0, 0)), resultado.resumen());
    assertEquals(Optional.of(T.plusSeconds(5)), resultado.iniciadoEn());
    assertEquals(Optional.of(T.plusSeconds(5)), resultado.terminadoEn());
    assertEquals(2, mensajes.listarDeLote(lote.id()).size());
    assertEquals(3, transacciones.veces(), "tomar, registrar y cerrar: tres transacciones");
  }

  /** El error queda escrito en el lote para el panel, y se propaga para el log. */
  @Test
  void unArchivoIlegibleDejaElLoteEnErrorConElMotivoYPropaga() {
    FuenteFija fuente = new FuenteFija(new ExportacionIlegibleException("El zip no trae .txt."));
    ProcesarLoteDeIngesta caso = casoCon(fuente);

    assertThrows(ExportacionIlegibleException.class, () -> caso.ejecutar(lote.id()));

    LoteIngesta guardado = lotes.buscarPorId(lote.id()).orElseThrow();
    assertEquals(EstadoLote.ERROR, guardado.estado());
    assertEquals(Optional.of("El zip no trae .txt."), guardado.detalleError());
    assertEquals(Optional.of(T.plusSeconds(5)), guardado.terminadoEn());
  }

  /** Un fallo del programa no le enseña al panel el mensaje de una excepción de Java. */
  @Test
  void unFalloInesperadoSeEscribeComoTalSinElMensajeCrudo() {
    FuenteFija fuente = new FuenteFija(new NullPointerException("Cannot invoke \"x.y()\""));
    ProcesarLoteDeIngesta caso = casoCon(fuente);

    assertThrows(NullPointerException.class, () -> caso.ejecutar(lote.id()));

    String detalle = lotes.buscarPorId(lote.id()).orElseThrow().detalleError().orElseThrow();
    assertTrue(detalle.contains("Error inesperado"), detalle);
    assertTrue(detalle.contains("NullPointerException"), detalle);
    assertTrue(!detalle.contains("Cannot invoke"), detalle);
  }

  /** El ejecutor puede entregar dos veces el mismo id; la segunda no reprocesa. */
  @Test
  void unLoteQueYaNoEstaEnLaColaSeDevuelveSinTocar() {
    FuenteFija fuente = new FuenteFija(List.of());
    ProcesarLoteDeIngesta caso = casoCon(fuente);
    caso.ejecutar(lote.id());
    int actualizaciones = lotes.actualizaciones;

    LoteIngesta otraVez = caso.ejecutar(lote.id());

    assertEquals(EstadoLote.TERMINADO, otraVez.estado());
    assertEquals(actualizaciones, lotes.actualizaciones);
    assertEquals(1, fuente.lecturas, "el archivo se lee una sola vez");
  }

  @Test
  void sinLoteRevienta() {
    ProcesarLoteDeIngesta caso = casoCon(new FuenteFija(List.of()));

    assertThrows(LoteNoEncontradoException.class, () -> caso.ejecutar(UUID.randomUUID()));
  }
}
