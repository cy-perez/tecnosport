package co.tecnosport.api.domain.proveedores;

import static co.tecnosport.api.domain.proveedores.OrdenDePublicacion.FOTOS_PRIMERO;
import static co.tecnosport.api.domain.proveedores.OrdenDePublicacion.TEXTO_PRIMERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgrupadorDePublicacionesTest {

  private static final Instant T = Instant.parse("2026-09-28T15:15:00Z");
  private static final UUID PROVEEDOR = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a6");
  private static final UUID LOTE = UUID.fromString("01a0ca12-ce7f-7ae2-95e8-e6e0127dc7a7");
  private static final Duration QUINCE_MINUTOS = Duration.ofMinutes(15);

  private int contador;

  private IdExternoDeMensaje id() {
    return new IdExternoDeMensaje("m-" + (++contador));
  }

  private MensajeProveedor texto(int segundos, String texto) {
    return MensajeProveedor.texto(PROVEEDOR, LOTE, id(), T.plusSeconds(segundos), texto);
  }

  private MensajeProveedor foto(int segundos, String pie) {
    return MensajeProveedor.imagen(
        PROVEEDOR, LOTE, id(), T.plusSeconds(segundos), pie, "proveedores/x/" + contador + ".jpg");
  }

  private MensajeProveedor fotoOmitida(int segundos) {
    return MensajeProveedor.imagenOmitida(PROVEEDOR, LOTE, id(), T.plusSeconds(segundos), null);
  }

  private MensajeProveedor audio(int segundos) {
    return MensajeProveedor.otro(PROVEEDOR, LOTE, id(), T.plusSeconds(segundos), null, true);
  }

  /** La forma del chat de bolsos: texto con precio y luego de una a cuatro fotos. */
  @Test
  void unTextoConPrecioAbreYLasFotosQueSiguenSonSuyas() {
    MensajeProveedor bolso = texto(0, "Bolso de dama 💰 *53.000*");
    MensajeProveedor foto1 = foto(20, null);
    MensajeProveedor foto2 = foto(70, null);
    MensajeProveedor morral = texto(600, "Morral fino 💰52.000");
    MensajeProveedor foto3 = foto(610, null);

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, foto1, foto2, morral, foto3), FOTOS_PRIMERO);

    assertEquals(2, resultado.publicaciones().size());
    assertEquals(0, resultado.sueltos());
    PublicacionProveedor primera = resultado.publicaciones().get(0);
    assertEquals(bolso.id(), primera.mensajePrincipalId());
    assertEquals(List.of(foto1.id(), foto2.id()), primera.medios());
    assertEquals(List.of(), primera.textosAdicionales());
    assertEquals(T, primera.fecha());
    assertEquals(EstadoPublicacionProveedor.PENDIENTE_EXTRACCION, primera.estado());
    assertEquals(List.of(foto3.id()), resultado.publicaciones().get(1).medios());
  }

  @Test
  void unTextoSinPrecioDentroDeLaVentanaEsUnTextoAdicional() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor nota = texto(30, "Este viene con la tira en cuero");
    MensajeProveedor omitida = fotoOmitida(40);

    PublicacionProveedor publicacion =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, nota, omitida), FOTOS_PRIMERO)
            .publicaciones()
            .get(0);

    assertEquals(List.of(nota.id()), publicacion.textosAdicionales());
    assertEquals(List.of(omitida.id()), publicacion.medios(), "la omitida cuenta como medio");
  }

  /** Un pie de foto con precio abre publicación, y esa foto es su primer medio. */
  @Test
  void unaFotoConPrecioEnElPieAbreYEsSuPrimerMedio() {
    MensajeProveedor conPie = foto(0, "Canguro 💰 35.000");
    MensajeProveedor otra = foto(15, null);

    PublicacionProveedor publicacion =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(conPie, otra), FOTOS_PRIMERO)
            .publicaciones()
            .get(0);

    assertEquals(conPie.id(), publicacion.mensajePrincipalId());
    assertEquals(List.of(conPie.id(), otra.id()), publicacion.medios());
  }

  /** La ventana corta el silencio: se mide desde el último anexado, no desde el principal. */
  @Test
  void laVentanaSeMideDesdeElUltimoMensajeAnexado() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor foto1 = foto(14 * 60, null);
    MensajeProveedor foto2 = foto(27 * 60, null);
    MensajeProveedor tarde = foto(50 * 60, null);

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, foto1, foto2, tarde), FOTOS_PRIMERO);

    assertEquals(List.of(foto1.id(), foto2.id()), resultado.publicaciones().get(0).medios());
    assertEquals(1, resultado.sueltos());
  }

  /**
   * La forma de los chats reales (30 de septiembre de 2026): el álbum de fotos sale primero y el
   * texto con el precio después, al minuto. Cada tanda es de su texto, no del anterior.
   */
  @Test
  void lasFotosQueLleganAntesDelTextoConPrecioSonSuyas() {
    MensajeProveedor foto1 = foto(0, null);
    MensajeProveedor foto2 = foto(5, null);
    MensajeProveedor bolso = texto(60, "Bolso de dama 💰 53.000");
    MensajeProveedor foto3 = foto(2 * 3600, null);
    MensajeProveedor foto4 = foto(2 * 3600 + 10, null);
    MensajeProveedor manosLibres = texto(2 * 3600 + 60, "Manos libres 💰 45.000");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(foto1, foto2, bolso, foto3, foto4, manosLibres), FOTOS_PRIMERO);

    assertEquals(2, resultado.publicaciones().size());
    assertEquals(0, resultado.sueltos());
    assertEquals(List.of(foto1.id(), foto2.id()), resultado.publicaciones().get(0).medios());
    assertEquals(bolso.id(), resultado.publicaciones().get(0).mensajePrincipalId());
    assertEquals(List.of(foto3.id(), foto4.id()), resultado.publicaciones().get(1).medios());
  }

  /** Entre dos precios cercanos, la foto es del más cercano. */
  @Test
  void entreDosPreciosLaFotoEsDelMasCercano() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor delBolso = foto(60, null);
    MensajeProveedor delMorral = foto(240, null);
    MensajeProveedor tambienDelMorral = foto(300, null);
    MensajeProveedor morral = texto(360, "Morral 💰 52.000");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, delBolso, delMorral, tambienDelMorral, morral), FOTOS_PRIMERO);

    // 60 s: a un minuto del bolso y a cinco del morral. 240 s: a cuatro del bolso y a dos del
    // morral. La distancia se mide al precio de cada lado, no a la última foto anexada.
    assertEquals(List.of(delBolso.id()), resultado.publicaciones().get(0).medios());
    assertEquals(
        List.of(delMorral.id(), tambienDelMorral.id()), resultado.publicaciones().get(1).medios());
  }

  /**
   * La forma de la exportación de Imperio Wicho (3 de octubre de 2026): Android no trae segundos, y
   * en el mismo minuto llegan el precio de un producto, la foto del siguiente y el precio de ese
   * siguiente. La foto queda a cero de los dos, y es del de después: la foto sale antes que su
   * texto.
   */
  @Test
  void enElMismoMinutoLaFotoEsDelPrecioDeDespues() {
    MensajeProveedor caballero = texto(0, "Importado AAA Caballero 💰*$115,000*");
    MensajeProveedor laSuperstar = foto(0, null);
    MensajeProveedor superstar = texto(0, "Superstar Importado AAA 💰 *$105,000*");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(caballero, laSuperstar, superstar), FOTOS_PRIMERO);

    assertEquals(List.of(), resultado.publicaciones().get(0).medios());
    assertEquals(superstar.id(), resultado.publicaciones().get(1).mensajePrincipalId());
    assertEquals(List.of(laSuperstar.id()), resultado.publicaciones().get(1).medios());
  }

  /** El empate a la misma distancia sin ser cero se decide igual: hacia el precio de después. */
  @Test
  void aIgualDistanciaDeDosPreciosLaFotoEsDelDeDespues() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor enMedio = foto(180, null);
    MensajeProveedor morral = texto(360, "Morral 💰 52.000");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, enMedio, morral), FOTOS_PRIMERO);

    assertEquals(List.of(), resultado.publicaciones().get(0).medios());
    assertEquals(List.of(enMedio.id()), resultado.publicaciones().get(1).medios());
  }

  /**
   * La forma de la exportación de La Riverah (5 de octubre de 2026): el texto con el precio y un
   * «👇👇👇», y después sus fotos, dos productos en el mismo minuto. Con el orden del proveedor,
   * cada foto es del precio que la precede.
   */
  @Test
  void conElTextoPrimeroLaFotoDelMismoMinutoEsDelPrecioDeAntes() {
    MensajeProveedor cuero = texto(0, "*Jeans Efecto cuero negro para dama* 🤑$68.000 🥳");
    MensajeProveedor delCuero = foto(0, null);
    MensajeProveedor blanco = texto(0, "*Jeans blanco para dama* 🤑$68.000 🥳");
    MensajeProveedor delBlanco = foto(0, null);
    MensajeProveedor overol = texto(14 * 60, "*Overol Licrado DAMA* 🤑$ 68.000🥳🥳");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(cuero, delCuero, blanco, delBlanco, overol), TEXTO_PRIMERO);

    assertEquals(List.of(delCuero.id()), resultado.publicaciones().get(0).medios());
    assertEquals(List.of(delBlanco.id()), resultado.publicaciones().get(1).medios());
    assertEquals(List.of(), resultado.publicaciones().get(2).medios());
  }

  /** Los mismos mensajes con el orden contrario: es el orden lo que decide, no la casualidad. */
  @Test
  void conLasFotosPrimeroLosMismosMensajesSeRepartenAlReves() {
    MensajeProveedor cuero = texto(0, "*Jeans Efecto cuero negro para dama* 🤑$68.000 🥳");
    MensajeProveedor primera = foto(0, null);
    MensajeProveedor blanco = texto(0, "*Jeans blanco para dama* 🤑$68.000 🥳");
    MensajeProveedor segunda = foto(0, null);

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(cuero, primera, blanco, segunda), FOTOS_PRIMERO);

    assertEquals(List.of(), resultado.publicaciones().get(0).medios());
    assertEquals(List.of(primera.id(), segunda.id()), resultado.publicaciones().get(1).medios());
  }

  /** El empate a la misma distancia sin ser cero sigue al orden igual que el de cero. */
  @Test
  void conElTextoPrimeroAIgualDistanciaLaFotoEsDelDeAntes() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor enMedio = foto(180, null);
    MensajeProveedor morral = texto(360, "Morral 💰 52.000");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, enMedio, morral), TEXTO_PRIMERO);

    assertEquals(List.of(enMedio.id()), resultado.publicaciones().get(0).medios());
    assertEquals(List.of(), resultado.publicaciones().get(1).medios());
  }

  /**
   * Fuera del empate el orden no cuenta: la foto a un minuto del precio siguiente y a cinco del
   * anterior es del siguiente aunque el proveedor publique primero el texto. «Siempre al anterior»,
   * probado contra la exportación real de Imperio Wicho, dejaba dos publicaciones sin foto.
   */
  @Test
  void conElTextoPrimeroFueraDelEmpateGanaElMasCercano() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor cercaDelMorral = foto(300, null);
    MensajeProveedor morral = texto(360, "Morral 💰 52.000");

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(bolso, cercaDelMorral, morral), TEXTO_PRIMERO);

    assertEquals(List.of(), resultado.publicaciones().get(0).medios());
    assertEquals(List.of(cercaDelMorral.id()), resultado.publicaciones().get(1).medios());
  }

  @Test
  void elOrdenEsObligatorio() {
    AgrupadorDePublicaciones agrupador = new AgrupadorDePublicaciones(QUINCE_MINUTOS);

    assertThrows(NullPointerException.class, () -> agrupador.agrupar(List.of(), null));
  }

  @Test
  void loQueQuedaLejosDeCualquierPrecioQuedaSueltoYUnAudioNoSeAnexa() {
    MensajeProveedor huerfana = foto(0, null);
    MensajeProveedor bolso = texto(3600, "Bolso 💰 53.000");
    MensajeProveedor nota = audio(3610);
    MensajeProveedor foto = foto(3620, null);

    AgrupadorDePublicaciones.Resultado resultado =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(List.of(huerfana, bolso, nota, foto), FOTOS_PRIMERO);

    assertEquals(1, resultado.publicaciones().size());
    assertEquals(2, resultado.sueltos());
    assertEquals(List.of(foto.id()), resultado.publicaciones().get(0).medios());
  }

  @Test
  void ordenaPorFechaAunqueLleguenDesordenados() {
    MensajeProveedor bolso = texto(0, "Bolso 💰 53.000");
    MensajeProveedor foto = foto(30, null);
    List<MensajeProveedor> desordenados = new ArrayList<>(List.of(foto, bolso));

    PublicacionProveedor publicacion =
        new AgrupadorDePublicaciones(QUINCE_MINUTOS)
            .agrupar(desordenados, FOTOS_PRIMERO)
            .publicaciones()
            .get(0);

    assertEquals(bolso.id(), publicacion.mensajePrincipalId());
    assertEquals(List.of(foto.id()), publicacion.medios());
  }

  @Test
  void esDeterminista() {
    List<MensajeProveedor> mensajes =
        List.of(texto(0, "Bolso 💰 53.000"), foto(10, null), texto(700, "Morral 💰 40.000"));
    AgrupadorDePublicaciones agrupador = new AgrupadorDePublicaciones(QUINCE_MINUTOS);

    List<UUID> una =
        agrupador.agrupar(mensajes, FOTOS_PRIMERO).publicaciones().stream()
            .map(PublicacionProveedor::mensajePrincipalId)
            .toList();
    List<UUID> otra =
        agrupador.agrupar(mensajes, FOTOS_PRIMERO).publicaciones().stream()
            .map(PublicacionProveedor::mensajePrincipalId)
            .toList();

    assertEquals(una, otra);
  }

  @Test
  void laVentanaTieneQueSerPositiva() {
    assertThrows(IllegalArgumentException.class, () -> new AgrupadorDePublicaciones(Duration.ZERO));
    assertTrue(
        new AgrupadorDePublicaciones(Duration.ofSeconds(1))
            .agrupar(List.of(), FOTOS_PRIMERO)
            .publicaciones()
            .isEmpty());
  }
}
