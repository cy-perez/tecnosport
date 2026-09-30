package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.AlmacenEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioLotesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioMensajesEnMemoria;
import co.tecnosport.api.application.proveedores.ApoyoDeIngesta.RepositorioProveedoresEnMemoria;
import co.tecnosport.api.domain.proveedores.LoteIngesta;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.Proveedor;
import co.tecnosport.api.domain.proveedores.TipoMensaje;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrarMensajesDeProveedorTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final String TEXTO = "*Nueva colección* 😍\nBolso de dama mediano 👜\n💰 *53.000*";

  private final RepositorioProveedoresEnMemoria proveedores = new RepositorioProveedoresEnMemoria();
  private final RepositorioLotesEnMemoria lotes = new RepositorioLotesEnMemoria();
  private final RepositorioMensajesEnMemoria mensajes = new RepositorioMensajesEnMemoria();
  private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
  private final RegistrarMensajesDeProveedor caso =
      new RegistrarMensajesDeProveedor(proveedores, lotes, mensajes, almacen);

  private Proveedor proveedor;
  private LoteIngesta lote;

  @BeforeEach
  void unProveedorYUnLote() {
    proveedor = ApoyoDeIngesta.proveedorDeBolsos();
    proveedores.guardar(proveedor);
    lote = LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/a.zip", T);
    lotes.guardar(lote);
  }

  private static List<MensajeCrudo> unProductoConDosFotosYUnaRespuestaNuestra() {
    return List.of(
        MensajeCrudo.texto(T, ApoyoDeIngesta.REMITENTE, TEXTO),
        MensajeCrudo.imagen(
            T.plusSeconds(30),
            ApoyoDeIngesta.REMITENTE,
            null,
            ApoyoDeIngesta.foto("IMG-20260928-WA0012.jpg")),
        MensajeCrudo.imagen(
            T.plusSeconds(40),
            ApoyoDeIngesta.REMITENTE,
            null,
            ApoyoDeIngesta.foto("IMG-20260928-WA0013.jpg")),
        MensajeCrudo.texto(T.plusSeconds(90), "Tecno Sport", "Me mandas 3 del negro"));
  }

  @Test
  void registraLosDelProveedorYCuentaLosNuestrosComoIgnorados() {
    MensajesRegistrados resultado =
        caso.ejecutar(lote.id(), unProductoConDosFotosYUnaRespuestaNuestra());

    assertEquals(4, resultado.leidos());
    assertEquals(1, resultado.ignorados());
    assertEquals(3, resultado.cuantosNuevos());
    assertEquals(3, mensajes.listarDeLote(lote.id()).size());
    MensajeProveedor texto = mensajes.listarDeLote(lote.id()).get(0);
    assertEquals(TipoMensaje.TEXTO, texto.tipo());
    assertEquals(Optional.of(TEXTO), texto.texto());
    assertEquals(lote.id(), texto.loteId());
  }

  /** El segundo criterio de aceptación: la misma exportación dos veces no crea nada. */
  @Test
  void volverARegistrarLoMismoNoCreaMensajes() {
    caso.ejecutar(lote.id(), unProductoConDosFotosYUnaRespuestaNuestra());
    LoteIngesta segundo =
        LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/b.zip", T);
    lotes.guardar(segundo);

    MensajesRegistrados resultado =
        caso.ejecutar(segundo.id(), unProductoConDosFotosYUnaRespuestaNuestra());

    assertEquals(4, resultado.leidos());
    assertEquals(0, resultado.cuantosNuevos());
    assertEquals(3, mensajes.guardados.size());
    assertEquals(2, almacen.objetos.size(), "tampoco vuelve a subir las fotos");
  }

  @Test
  void lasFotosSeGuardanBajoElMesDeEnvioConElIdDelMensaje() {
    caso.ejecutar(lote.id(), unProductoConDosFotosYUnaRespuestaNuestra());

    MensajeProveedor foto = mensajes.listarDeLote(lote.id()).get(1);
    String referencia = foto.referenciaArchivo().orElseThrow();
    assertEquals("proveedores/" + proveedor.id() + "/2026/09/" + foto.id() + ".jpg", referencia);
    assertArrayEquals(
        "bytes de IMG-20260928-WA0012.jpg".getBytes(), almacen.objetos.get(referencia));
    assertEquals("image/jpeg", almacen.tipos.get(referencia));
  }

  /**
   * Un mensaje de las 11 de la noche del 30 en Bogotá es del 30, aunque en UTC ya sea el 1 del mes
   * siguiente.
   */
  @Test
  void elMesDeLaKeyEsElDeBogota() {
    Instant casiMedianocheDel30 = Instant.parse("2026-10-01T03:30:00Z");
    caso.ejecutar(
        lote.id(),
        List.of(
            MensajeCrudo.imagen(
                casiMedianocheDel30,
                ApoyoDeIngesta.REMITENTE,
                null,
                ApoyoDeIngesta.foto("a.jpg"))));

    String referencia = mensajes.listarDeLote(lote.id()).get(0).referenciaArchivo().orElseThrow();
    assertTrue(referencia.contains("/2026/09/"), referencia);
  }

  /**
   * Cuatro fotos omitidas del mismo minuto son cuatro mensajes, no uno: sin el número dentro del
   * minuto el id fabricado sería el mismo y la publicación se quedaría con una sola foto.
   */
  @Test
  void lasFotosOmitidasDelMismoMinutoNoColapsan() {
    List<MensajeCrudo> crudos =
        List.of(
            MensajeCrudo.texto(T, ApoyoDeIngesta.REMITENTE, TEXTO),
            MensajeCrudo.imagenOmitida(T, ApoyoDeIngesta.REMITENTE, null),
            MensajeCrudo.imagenOmitida(T, ApoyoDeIngesta.REMITENTE, null),
            MensajeCrudo.imagenOmitida(T, ApoyoDeIngesta.REMITENTE, null),
            MensajeCrudo.imagenOmitida(T, ApoyoDeIngesta.REMITENTE, null));

    MensajesRegistrados resultado = caso.ejecutar(lote.id(), crudos);

    assertEquals(5, resultado.cuantosNuevos());
    assertTrue(resultado.nuevos().get(1).medioOmitido());
    assertEquals(0, almacen.objetos.size());

    // Y es estable: la misma lista otra vez no registra nada.
    LoteIngesta otro =
        LoteIngesta.recibirExportacion(proveedor.id(), "proveedores/x/exportaciones/b.zip", T);
    lotes.guardar(otro);
    assertEquals(0, caso.ejecutar(otro.id(), crudos).cuantosNuevos());
  }

  /**
   * El mismo texto reenviado en el mismo minuto es un reenvío, y dentro del lote solo entra una
   * vez.
   */
  @Test
  void unRepetidoDentroDelMismoLoteSeRegistraUnaVez() {
    MensajesRegistrados resultado =
        caso.ejecutar(
            lote.id(),
            List.of(
                MensajeCrudo.texto(T, ApoyoDeIngesta.REMITENTE, TEXTO),
                MensajeCrudo.texto(T, ApoyoDeIngesta.REMITENTE, TEXTO)));

    assertEquals(1, resultado.cuantosNuevos());
  }

  /** Cuando la fuente ya trae identificador —el webhook— ese manda sobre el fabricado. */
  @Test
  void elIdExternoDeLaFuenteMandaSobreElFabricado() {
    MensajeCrudo conId =
        new MensajeCrudo(
            T, ApoyoDeIngesta.REMITENTE, TipoMensaje.TEXTO, TEXTO, null, null, false, "wamid.ABC");

    MensajesRegistrados resultado = caso.ejecutar(lote.id(), List.of(conId));

    assertEquals("wamid.ABC", resultado.nuevos().get(0).idExterno().valor());
  }

  @Test
  void unMensajeDeOtroTipoSeRegistraSinArchivo() {
    MensajesRegistrados resultado =
        caso.ejecutar(
            lote.id(), List.of(MensajeCrudo.otro(T, ApoyoDeIngesta.REMITENTE, null, true)));

    assertEquals(TipoMensaje.OTRO, resultado.nuevos().get(0).tipo());
    assertTrue(resultado.nuevos().get(0).medioOmitido());
  }

  @Test
  void sinLoteNoSeRegistraNada() {
    assertThrows(
        LoteNoEncontradoException.class, () -> caso.ejecutar(UUID.randomUUID(), List.of()));
  }
}
