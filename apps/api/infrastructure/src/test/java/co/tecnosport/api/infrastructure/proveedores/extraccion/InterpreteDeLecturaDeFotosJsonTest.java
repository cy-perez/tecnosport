package co.tecnosport.api.infrastructure.proveedores.extraccion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.tecnosport.api.domain.proveedores.GeneroDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeCalzado;
import co.tecnosport.api.domain.proveedores.LecturaDeFotos;
import co.tecnosport.api.domain.proveedores.LecturaDePrenda;
import co.tecnosport.api.domain.proveedores.TipoDeCalzado;
import co.tecnosport.api.domain.proveedores.TipoProductoProveedor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** La lectura que el borrador guardó, leída otra vez al abrirlo en el panel. */
class InterpreteDeLecturaDeFotosJsonTest {

  private final InterpreteDeLecturaDeFotosJson interprete = new InterpreteDeLecturaDeFotosJson();

  @Test
  void losColoresDeCadaFotoSalenConSuPosicion() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"album_de_disenos\":false,\"fotos\":["
                    + "{\"foto\":1,\"colores\":[\"Negro\",\"cocoa\",\"verde\"]},"
                    + "{\"foto\":2,\"colores\":[\"negro\"]}]}")
            .orElseThrow();

    assertEquals(0, lectura.fotos().get(0).posicion());
    assertEquals(List.of("negro", "cocoa", "verde"), lectura.fotos().get(0).colores());
    assertEquals(List.of("negro"), lectura.fotos().get(1).colores());
  }

  /**
   * La prenda y su género (29 de octubre de 2026); lo que no es ropa y lo viejo quedan sin ella.
   */
  @Test
  void laPrendaSeLeeYLoQueNoEsRopaNiLoViejoQuedanSinElla() {
    LecturaDeFotos camisa =
        interprete
            .interpretar("{\"prenda\":{\"tipo\":\"camisa\",\"genero\":\"dama\"},\"fotos\":[]}")
            .orElseThrow();
    LecturaDeFotos soloGenero =
        interprete
            .interpretar(
                "{\"prenda\":{\"tipo\":\"otra_prenda\",\"genero\":\"caballero\"},"
                    + "\"fotos\":[]}")
            .orElseThrow();
    LecturaDeFotos tenis =
        interprete
            .interpretar("{\"prenda\":{\"tipo\":\"no_es_ropa\",\"genero\":\"dama\"},\"fotos\":[]}")
            .orElseThrow();
    LecturaDeFotos vieja = interprete.interpretar("{\"fotos\":[]}").orElseThrow();

    assertEquals(
        Optional.of(new LecturaDePrenda(TipoProductoProveedor.CAMISA, GeneroDeCalzado.DAMA)),
        camisa.prendaOpcional());
    assertEquals(
        Optional.of(new LecturaDePrenda(null, GeneroDeCalzado.CABALLERO)),
        soloGenero.prendaOpcional());
    assertEquals(Optional.empty(), tenis.prendaOpcional());
    assertEquals(Optional.empty(), vieja.prendaOpcional());
  }

  /** El rótulo de tallas de la esquina (13 de octubre de 2026); las lecturas viejas no lo traen. */
  @Test
  void lasTallasImpresasSeLeenYLasViejasNoLasTraen() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"fotos\":[{\"foto\":1,\"tallas\":[\"35\",\"36\",\" 38 \"]},"
                    + "{\"foto\":2,\"colores\":[\"negro\"]}]}")
            .orElseThrow();

    assertEquals(List.of("35", "36", "38"), lectura.fotos().get(0).tallasImpresas());
    assertEquals(List.of(), lectura.fotos().get(1).tallasImpresas());
  }

  /** Una lectura vieja o rota no impide revisar el borrador: no hay tonos que proponer, y ya. */
  /**
   * El calzado de Tenis Al Pie (ADR-0089): un tenis con su modelo y su género, una Crocs que no se
   * vende, una blusa que no es calzado y una lectura vieja sin el campo.
   */
  @Test
  void elCalzadoSeLeeYLoQueNoEsCalzadoQuedaSinLectura() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"fotos\":["
                    + "{\"foto\":1,\"calzado\":{\"tipo\":\"tenis\",\"modelo\":\"Air Force 1\","
                    + "\"modelo_seguro\":true,\"genero\":\"unisex\",\"foto_publicable\":true}},"
                    + "{\"foto\":2,\"calzado\":{\"tipo\":\"zueco\",\"modelo\":\"\","
                    + "\"modelo_seguro\":false,\"genero\":\"no_se_sabe\","
                    + "\"foto_publicable\":false}},"
                    + "{\"foto\":3,\"calzado\":{\"tipo\":\"no_es_calzado\",\"modelo\":\"\","
                    + "\"modelo_seguro\":false,\"genero\":\"no_se_sabe\","
                    + "\"foto_publicable\":false}},"
                    + "{\"foto\":4,\"colores\":[\"negro\"]}]}")
            .orElseThrow();

    LecturaDeCalzado tenis = lectura.fotos().get(0).calzadoOpcional().orElseThrow();
    assertEquals(TipoDeCalzado.TENIS, tenis.tipo());
    assertEquals(Optional.of("Air Force 1"), tenis.modeloParaElTitulo());
    assertEquals(Optional.of(GeneroDeCalzado.UNISEX), tenis.generoOpcional());
    assertTrue(tenis.fotoPublicable());

    LecturaDeCalzado crocs = lectura.fotos().get(1).calzadoOpcional().orElseThrow();
    assertEquals(TipoDeCalzado.ZUECO, crocs.tipo());
    assertEquals(Optional.empty(), crocs.generoOpcional());
    assertEquals(Optional.empty(), crocs.modeloParaElTitulo());

    assertTrue(lectura.fotos().get(2).calzadoOpcional().isEmpty());
    assertTrue(lectura.fotos().get(3).calzadoOpcional().isEmpty());
  }

  /**
   * Un modelo del que el lector duda va al título igual, por confirmar (ADR-0090); y la marca por
   * parecido y el grupo se leen, con el número de foto pasado a posición.
   */
  @Test
  void unModeloDudosoVaAlTituloPorConfirmarConSuMarcaYSuGrupo() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"fotos\":[{\"foto\":3,\"calzado\":{\"tipo\":\"tenis\",\"marca\":\"Jordan\",\"modelo\":\"Jordan 4\","
                    + "\"modelo_seguro\":false,\"genero\":\"caballero\","
                    + "\"producto_solo\":true,\"mismo_tenis_que\":1}}]}")
            .orElseThrow();

    LecturaDeCalzado calzado = lectura.fotos().get(0).calzadoOpcional().orElseThrow();
    assertEquals(Optional.of("Jordan 4"), calzado.modeloParaElTitulo());
    assertTrue(calzado.modeloPorConfirmar());
    assertEquals(Optional.of("Jordan"), calzado.marcaOpcional());
    assertEquals(Optional.of(0), calzado.mismoTenisQueOpcional());
  }

  /** Una lectura de antes, o un número que no es de foto: sin grupo, y la foto queda sola. */
  @Test
  void sinGrupoONumeroInvalidoNoHayGrupo() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"fotos\":[{\"foto\":1,\"calzado\":{\"tipo\":\"tenis\",\"modelo\":\"\","
                    + "\"modelo_seguro\":false,\"genero\":\"no_se_sabe\",\"producto_solo\":true}},"
                    + "{\"foto\":2,\"calzado\":{\"tipo\":\"tenis\",\"marca\":\"\",\"modelo\":\"\","
                    + "\"modelo_seguro\":false,\"genero\":\"no_se_sabe\",\"producto_solo\":true,"
                    + "\"mismo_tenis_que\":0}}]}")
            .orElseThrow();

    assertTrue(
        lectura.fotos().get(0).calzadoOpcional().orElseThrow().mismoTenisQueOpcional().isEmpty());
    LecturaDeCalzado segunda = lectura.fotos().get(1).calzadoOpcional().orElseThrow();
    assertTrue(segunda.mismoTenisQueOpcional().isEmpty());
    assertTrue(segunda.marcaOpcional().isEmpty());
  }

  /**
   * Desde el 26 de octubre de 2026 (ADR-0091) el lector dice si la foto se puede publicar; una
   * lectura guardada antes decía si el tenis se veía solo, y se sigue entendiendo así.
   */
  @Test
  void laFotoPublicableSeLeeYLaLecturaViejaDiceSiSeVeiaSolo() {
    LecturaDeFotos lectura =
        interprete
            .interpretar(
                "{\"fotos\":[{\"foto\":1,\"calzado\":{\"tipo\":\"tenis\",\"modelo\":\"\",\"modelo_seguro\":false,"
                    + "\"genero\":\"unisex\",\"foto_publicable\":true,\"producto_solo\":false}},"
                    + "{\"foto\":2,\"calzado\":{\"tipo\":\"tenis\",\"modelo\":\"\",\"modelo_seguro\":false,"
                    + "\"genero\":\"unisex\",\"producto_solo\":false}}]}")
            .orElseThrow();

    assertTrue(lectura.fotos().get(0).calzadoOpcional().orElseThrow().fotoPublicable());
    assertFalse(lectura.fotos().get(1).calzadoOpcional().orElseThrow().fotoPublicable());
  }

  @Test
  void loQueNoSeEntiendeNoEsUnaLectura() {
    assertTrue(interprete.interpretar("no es json").isEmpty());
    assertTrue(interprete.interpretar("{\"sin\":\"fotos\"}").isEmpty());
    assertTrue(interprete.interpretar("").isEmpty());
    assertTrue(interprete.interpretar(null).isEmpty());
  }

  @Test
  void unaFotoSinNumeroNoSeLee() {
    LecturaDeFotos lectura =
        interprete.interpretar("{\"fotos\":[{\"colores\":[\"rojo\",\"azul\"]}]}").orElseThrow();

    assertTrue(lectura.fotos().isEmpty());
  }
}
