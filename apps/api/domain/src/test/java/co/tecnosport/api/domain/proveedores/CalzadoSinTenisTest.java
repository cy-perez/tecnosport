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
}
