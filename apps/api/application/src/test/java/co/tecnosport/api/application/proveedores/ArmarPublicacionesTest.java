package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArmarPublicacionesTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final ArmarPublicaciones caso =
      new ArmarPublicaciones(
          lotes, mensajes, publicaciones, new AgrupadorDePublicaciones(Duration.ofMinutes(15)));

  private LoteIngesta lote(String archivo) {
    LoteIngesta lote = LoteIngesta.recibirExportacion(PROVEEDOR, "p/exportaciones/" + archivo, T);
    lotes.guardar(lote);
    return lote;
  }

  /** Solo los mensajes de este lote: lo registrado en un lote anterior no vuelve a agruparse. */
  @Test
  void agrupaSoloLosMensajesDelLoteYGuardaLasPublicaciones() {
    LoteIngesta anterior = lote("a.zip");
    LoteIngesta actual = lote("b.zip");
    mensajes.guardarTodos(
        List.of(
            MensajeProveedor.texto(
                PROVEEDOR, anterior.id(), new IdExternoDeMensaje("viejo"), T, "Bolso 💰 53.000"),
            MensajeProveedor.texto(
                PROVEEDOR,
                actual.id(),
                new IdExternoDeMensaje("nuevo"),
                T.plusSeconds(3600),
                "Morral 💰 52.000"),
            MensajeProveedor.imagen(
                PROVEEDOR,
                actual.id(),
                new IdExternoDeMensaje("foto"),
                T.plusSeconds(3610),
                null,
                "p/2026/09/f.jpg")));

    AgrupadorDePublicaciones.Resultado resultado = caso.ejecutar(actual.id());

    assertEquals(1, resultado.publicaciones().size());
    assertEquals(1, publicaciones.listarDeLote(actual.id()).size());
    assertEquals(0, publicaciones.listarDeLote(anterior.id()).size());
    assertEquals(1, publicaciones.listarDeLote(actual.id()).get(0).medios().size());
  }

  @Test
  void unLoteSinMensajesNoDejaPublicaciones() {
    LoteIngesta lote = lote("a.zip");

    AgrupadorDePublicaciones.Resultado resultado = caso.ejecutar(lote.id());

    assertEquals(0, resultado.publicaciones().size());
    assertEquals(0, resultado.sueltos());
  }

  @Test
  void sinLoteRevienta() {
    assertThrows(LoteNoEncontradoException.class, () -> caso.ejecutar(UUID.randomUUID()));
  }
}
