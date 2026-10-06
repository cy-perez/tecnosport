package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioPublicacionesEnMemoria;
import co.tecnosport.api.domain.proveedores.AgrupadorDePublicaciones;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.OrdenDePublicacion;
import co.tecnosport.api.domain.proveedores.Proveedor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArmarPublicacionesTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private final Proveedor proveedor = ApoyoDeIngesta.proveedorDeBolsos();
  private final UUID proveedorId = proveedor.id();

  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final RepositorioPublicacionesEnMemoria publicaciones =
      new RepositorioPublicacionesEnMemoria();
  private final ArmarPublicaciones caso =
      new ArmarPublicaciones(
          lotes,
          proveedores,
          mensajes,
          publicaciones,
          new AgrupadorDePublicaciones(Duration.ofMinutes(15)));

  private LoteIngesta lote(String archivo) {
    proveedores.guardar(proveedor);
    LoteIngesta lote = LoteIngesta.recibirExportacion(proveedorId, "p/exportaciones/" + archivo, T);
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
                proveedorId, anterior.id(), new IdExternoDeMensaje("viejo"), T, "Bolso 💰 53.000"),
            MensajeProveedor.texto(
                proveedorId,
                actual.id(),
                new IdExternoDeMensaje("nuevo"),
                T.plusSeconds(3600),
                "Morral 💰 52.000"),
            MensajeProveedor.imagen(
                proveedorId,
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

  /**
   * El orden es del proveedor del lote, y llega al agrupador: la foto que empata entre dos precios
   * del mismo minuto es del de antes para quien publica primero el texto, y del de después para
   * quien publica primero las fotos.
   */
  @Test
  void laFotoQueEmpataSigueElOrdenDelProveedorDelLote() {
    LoteIngesta lote = lote("a.zip");
    MensajeProveedor cuero =
        MensajeProveedor.texto(
            proveedorId, lote.id(), new IdExternoDeMensaje("cuero"), T, "Jeans cuero 🤑$68.000");
    MensajeProveedor foto =
        MensajeProveedor.imagen(
            proveedorId, lote.id(), new IdExternoDeMensaje("foto"), T, null, "p/2026/10/f.jpg");
    MensajeProveedor blanco =
        MensajeProveedor.texto(
            proveedorId, lote.id(), new IdExternoDeMensaje("blanco"), T, "Jeans blanco 🤑$68.000");
    mensajes.guardarTodos(List.of(cuero, foto, blanco));

    editarOrden(OrdenDePublicacion.TEXTO_PRIMERO);
    AgrupadorDePublicaciones.Resultado textoPrimero = caso.ejecutar(lote.id());
    editarOrden(OrdenDePublicacion.FOTOS_PRIMERO);
    AgrupadorDePublicaciones.Resultado fotosPrimero = caso.ejecutar(lote.id());

    assertEquals(List.of(foto.id()), textoPrimero.publicaciones().get(0).medios());
    assertEquals(List.of(), textoPrimero.publicaciones().get(1).medios());
    assertEquals(List.of(), fotosPrimero.publicaciones().get(0).medios());
    assertEquals(List.of(foto.id()), fotosPrimero.publicaciones().get(1).medios());
  }

  @Test
  void sinProveedorRevienta() {
    LoteIngesta lote = LoteIngesta.recibirExportacion(UUID.randomUUID(), "p/x.zip", T);
    lotes.guardar(lote);

    assertThrows(ProveedorNoEncontradoException.class, () -> caso.ejecutar(lote.id()));
  }

  private void editarOrden(OrdenDePublicacion orden) {
    proveedor.editar(
        proveedor.nombre(),
        proveedor.linea(),
        proveedor.telefonoWhatsApp(),
        proveedor.nombreEnExportacion(),
        proveedor.activo(),
        proveedor.publicacionAutomatica(),
        proveedor.factorDeMargen().orElse(null),
        orden);
    proveedores.actualizar(proveedor);
  }

  @Test
  void sinLoteRevienta() {
    assertThrows(LoteNoEncontradoException.class, () -> caso.ejecutar(UUID.randomUUID()));
  }
}
