package co.tecnosport.api.application.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.proveedores.AlertaBorrador;
import co.tecnosport.api.domain.proveedores.IdExternoDeMensaje;
import co.tecnosport.api.domain.proveedores.MensajeProveedor;
import co.tecnosport.api.domain.proveedores.ProductoExtraido;
import co.tecnosport.api.domain.proveedores.PublicacionProveedor;
import co.tecnosport.api.domain.proveedores.Tallas;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExtraerProductoDePublicacionTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PROVEEDOR = UUID.randomUUID();
  private static final UUID LOTE = UUID.randomUUID();
  private static final BigDecimal UMBRAL = new BigDecimal("0.75");

  private final Map<UUID, MensajeProveedor> mensajes = new HashMap<>();
  private int contador;

  private MensajeProveedor texto(String texto) {
    MensajeProveedor m =
        MensajeProveedor.texto(
            PROVEEDOR,
            LOTE,
            new IdExternoDeMensaje("m" + (++contador)),
            T.plusSeconds(contador),
            texto);
    mensajes.put(m.id(), m);
    return m;
  }

  private MensajeProveedor foto(String referencia) {
    MensajeProveedor m =
        MensajeProveedor.imagen(
            PROVEEDOR,
            LOTE,
            new IdExternoDeMensaje("m" + (++contador)),
            T.plusSeconds(contador),
            null,
            referencia);
    mensajes.put(m.id(), m);
    return m;
  }

  private static ProductoExtraido extraido(String titulo, Long precio, String confianza) {
    return new ProductoExtraido(
        true,
        false,
        titulo,
        LineaCatalogo.BOLSOS,
        TipoProductoProveedor.BOLSO,
        precio == null ? null : Dinero.deCop(precio),
        Tallas.desconocida(),
        4,
        List.of(),
        "importado",
        List.of("incluye llavero"),
        new BigDecimal(confianza),
        null);
  }

  private ExtraccionEvaluada evaluar(PublicacionProveedor publicacion, ProductoExtraido respuesta) {
    ApoyoDeIngesta.ExtractorFalso extractor = new ApoyoDeIngesta.ExtractorFalso(respuesta);
    return new ExtraerProductoDePublicacion(extractor, UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.BOLSOS);
  }

  @Test
  void conTodoEnOrdenNoHayAlertasYElPrecioEsElDelTexto() {
    PublicacionProveedor publicacion =
        PublicacionProveedor.abrir(texto("Bolso de dama 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada =
        evaluar(publicacion, extraido("Bolso de dama mediano", 53000L, "0.92"));

    assertEquals(Set.of(), evaluada.alertas());
    assertEquals(Optional.of(Dinero.deCop(53000)), evaluada.precioProveedorOpcional());
    assertEquals("Bolso de dama mediano", evaluada.producto().titulo());
    assertEquals("{\"fixture\":true}", evaluada.jsonCrudo());
  }

  /** La salvaguarda que importa: el modelo dice 35.000 donde el texto dice 53.000. */
  @Test
  void siElExtractorDaOtroPrecioSeSigueConElDelTextoYSeAlerta() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 35000L, "0.9"));

    assertTrue(evaluada.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertEquals(Optional.of(Dinero.deCop(53000)), evaluada.precioProveedorOpcional());
  }

  @Test
  void siElExtractorNoDaPrecioSeSigueConElDelTextoYSeAlerta() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", null, "0.9"));

    assertEquals(Set.of(AlertaBorrador.PRECIO_INCONSISTENTE), evaluada.alertas());
    assertEquals(Optional.of(Dinero.deCop(53000)), evaluada.precioProveedorOpcional());
  }

  @Test
  void sinPrecioEnNingunLadoLaAlertaEsSinPrecio() {
    // Una publicación abierta por una foto cuyo pie tiene precio, y el texto adicional sin él.
    MensajeProveedor conPie =
        MensajeProveedor.imagen(
            PROVEEDOR,
            LOTE,
            new IdExternoDeMensaje("pie"),
            T,
            "Nuevo canguro",
            "proveedores/x/2.jpg");
    mensajes.put(conPie.id(), conPie);
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(conPie);

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Canguro", null, "0.9"));

    assertTrue(evaluada.alertas().contains(AlertaBorrador.SIN_PRECIO));
    assertFalse(evaluada.alertas().contains(AlertaBorrador.PRECIO_INCONSISTENTE));
    assertEquals(Optional.empty(), evaluada.precioProveedorOpcional());
  }

  @Test
  void tituloVacioTipoOtroConfianzaBajaYSinFotosAlertanCadaUnaPorSuLado() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("💰 *53.000*"));
    ProductoExtraido dudoso =
        new ProductoExtraido(
            true,
            false,
            "  ",
            null,
            TipoProductoProveedor.OTRO,
            Dinero.deCop(53000),
            null,
            null,
            null,
            null,
            null,
            new BigDecimal("0.40"),
            null);

    ExtraccionEvaluada evaluada = evaluar(publicacion, dudoso);

    assertEquals(
        Set.of(
            AlertaBorrador.TITULO_VACIO,
            AlertaBorrador.TIPO_DESCONOCIDO,
            AlertaBorrador.CONFIANZA_BAJA,
            AlertaBorrador.SIN_FOTOS),
        evaluada.alertas());
  }

  @Test
  void unaFotoOmitidaNoCuentaComoFoto() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    MensajeProveedor omitida =
        MensajeProveedor.imagenOmitida(
            PROVEEDOR, LOTE, new IdExternoDeMensaje("om"), T.plusSeconds(5), null);
    mensajes.put(omitida.id(), omitida);
    publicacion.anexar(omitida);

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 53000L, "0.9"));

    assertEquals(Set.of(AlertaBorrador.SIN_FOTOS), evaluada.alertas());
  }

  /** En el umbral exacto no hay alerta: el umbral es lo mínimo aceptable. */
  @Test
  void enElUmbralExactoLaConfianzaBasta() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(foto("proveedores/x/1.jpg"));

    ExtraccionEvaluada evaluada = evaluar(publicacion, extraido("Bolso", 53000L, "0.75"));

    assertFalse(evaluada.alertas().contains(AlertaBorrador.CONFIANZA_BAJA));
  }

  @Test
  void elExtractorRecibeElPrincipalYLosAdicionalesYLaLinea() {
    PublicacionProveedor publicacion = PublicacionProveedor.abrir(texto("Bolso 💰 *53.000*"));
    publicacion.anexar(texto("Este viene con la tira en cuero"));
    ApoyoDeIngesta.ExtractorFalso extractor =
        new ApoyoDeIngesta.ExtractorFalso(extraido("Bolso", 53000L, "0.9"));

    new ExtraerProductoDePublicacion(extractor, UMBRAL)
        .ejecutar(publicacion, mensajes, LineaCatalogo.BOLSOS);

    assertEquals("Bolso 💰 *53.000*", extractor.ultimoTexto.principal());
    assertEquals(List.of("Este viene con la tira en cuero"), extractor.ultimoTexto.adicionales());
    assertEquals(LineaCatalogo.BOLSOS, extractor.ultimoTexto.lineaDelProveedor());
    assertEquals(
        "Bolso 💰 *53.000*\n\nEste viene con la tira en cuero", extractor.ultimoTexto.completo());
  }

  @Test
  void elUmbralVaDeCeroAUno() {
    ApoyoDeIngesta.ExtractorFalso extractor =
        new ApoyoDeIngesta.ExtractorFalso(extraido("Bolso", 53000L, "0.9"));

    assertThrows(
        IllegalArgumentException.class,
        () -> new ExtraerProductoDePublicacion(extractor, new BigDecimal("1.5")));
  }
}
