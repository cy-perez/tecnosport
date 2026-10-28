package co.tecnosport.api.domain.proveedores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.domain.proveedores.LecturaDeFotos.LecturaDeFoto;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** El anuncio de calzado que no trae un tenis no deja borrador (ADR-0092). */
class CalzadoSinTenisTest {

  private static LecturaDeFoto foto(int posicion, TipoDeCalzado tipo) {
    return new LecturaDeFoto(
        posicion,
        List.of(),
        null,
        List.of(),
        List.of("Negro"),
        null,
        List.of(),
        List.of(),
        tipo == null ? null : new LecturaDeCalzado(null, false, null, tipo, true));
  }

  private static LecturaDeFotos lectura(LecturaDeFoto... fotos) {
    return new LecturaDeFotos(false, Arrays.asList(fotos), "{}");
  }

  @Test
  void unaChanclaSeDescartaDiciendoQueEsUnaChancla() {
    assertEquals(
        Optional.of(
            new CierreDePublicacion(MotivoDePublicacion.CALZADO_QUE_NO_ES_TENIS, "CHANCLA")),
        CalzadoSinTenis.en(
            lectura(foto(0, TipoDeCalzado.CHANCLA), foto(1, TipoDeCalzado.CHANCLA))));
  }

  @Test
  void dicePorElCalzadoQueMasSeVe() {
    assertEquals(
        Optional.of(
            new CierreDePublicacion(MotivoDePublicacion.CALZADO_QUE_NO_ES_TENIS, "SANDALIA")),
        CalzadoSinTenis.en(
            lectura(
                foto(0, TipoDeCalzado.CHANCLA),
                foto(1, TipoDeCalzado.SANDALIA),
                foto(2, TipoDeCalzado.SANDALIA))));
  }

  /** Con un tenis y una chancla no se sabe de cuál es el precio: lo decide quien revisa. */
  @Test
  void conUnTenisEntreLasFotosElAnuncioSigue() {
    assertEquals(
        Optional.empty(),
        CalzadoSinTenis.en(lectura(foto(0, TipoDeCalzado.CHANCLA), foto(1, TipoDeCalzado.TENIS))));
  }

  @Test
  void sinFotosOSinLecturaSeDescarta() {
    CierreDePublicacion sinLeer =
        CierreDePublicacion.de(MotivoDePublicacion.CALZADO_SIN_FOTOS_LEIDAS);
    assertEquals(Optional.of(sinLeer), CalzadoSinTenis.en(null));
    assertEquals(Optional.of(sinLeer), CalzadoSinTenis.en(lectura()));
  }

  @Test
  void fotosLeidasSinCalzadoNoDicenElTipo() {
    assertEquals(
        Optional.of(CierreDePublicacion.de(MotivoDePublicacion.CALZADO_QUE_NO_ES_TENIS)),
        CalzadoSinTenis.en(lectura(foto(0, null))));
  }

  // --- Por el texto, antes de leer nada (decisión 7) ---

  private static Optional<CierreDePublicacion> noEsTenis(String tipo) {
    return Optional.of(new CierreDePublicacion(MotivoDePublicacion.CALZADO_QUE_NO_ES_TENIS, tipo));
  }

  /** Imperio Wicho, 27 de octubre de 2026. */
  @Test
  void elTextoQueNombraUnaCholaOUnaSandaliaLaDescarta() {
    assertEquals(
        noEsTenis("CHANCLA"),
        CalzadoSinTenis.porElTexto(
            "Chola Playera Eva🧸🐻🧸\nTallas disponibles 36 a la 40!\n📦Docena *$120,000*"));
    assertEquals(
        noEsTenis("CHANCLA"),
        CalzadoSinTenis.porElTexto("PROMO 🚨🚨 PLAYERA ALO IMPORTADA👸🏻🌸\n💰*$25,000*"));
    assertEquals(
        noEsTenis("SANDALIA"),
        CalzadoSinTenis.porElTexto(
            "*Llegando Nuevamente*👦🏻⚽️⚽️\nSandalia Correa Deportiva niño🔥\nDetal 💰*$20,000*"));
    assertEquals(
        noEsTenis("SANDALIA"),
        CalzadoSinTenis.porElTexto("*Sandalias Bob y Elmo*✅\n Confort Dama y Caballero 🔥"));
  }

  @Test
  void cadaCalzadoQueNoSeVendeDiceCualEs() {
    assertEquals(noEsTenis("CHANCLA"), CalzadoSinTenis.porElTexto("CHANCLAS IMPORTADAS DAMA"));
    assertEquals(noEsTenis("ZUECO"), CalzadoSinTenis.porElTexto("Crocs clásicos 💰*$45,000*"));
    assertEquals(noEsTenis("BOTA"), CalzadoSinTenis.porElTexto("Botines de cuero caballero"));
    assertEquals(noEsTenis("OTRO"), CalzadoSinTenis.porElTexto("Pantuflas de peluche 🧸"));
  }

  @Test
  void losTenisDelMismoChatSiguen() {
    assertEquals(
        Optional.empty(),
        CalzadoSinTenis.porElTexto("*Deportivo Wishops Unisex*\nTallas disponibles 37 a la 43!"));
    assertEquals(
        Optional.empty(),
        CalzadoSinTenis.porElTexto("*IMPORTADO CABALLERO EVA*👟👟\n*$65,000* incluye caja 📦"));
    assertEquals(
        Optional.empty(),
        CalzadoSinTenis.porElTexto("PROMO 🚨🚨 IMPORTADO NIÑOS👧🏻👦🏻\n💰*$60,000*"));
    assertEquals(Optional.empty(), CalzadoSinTenis.porElTexto(null));
  }

  /** Con un tenis y una sandalia en el texto, deciden las fotos, como antes. */
  @Test
  void siElTextoDiceTenisDecidenLasFotos() {
    assertEquals(
        Optional.empty(), CalzadoSinTenis.porElTexto("Tenis y sandalias para dama 💰*$55,000*"));
  }

  /** La palabra entera: «Chocolate», «botón» o «playeritas» no son calzado. */
  @Test
  void soloCuentaLaPalabraEntera() {
    assertEquals(
        Optional.empty(), CalzadoSinTenis.porElTexto("Color chocolate, botón dorado, playeritas"));
  }
}
