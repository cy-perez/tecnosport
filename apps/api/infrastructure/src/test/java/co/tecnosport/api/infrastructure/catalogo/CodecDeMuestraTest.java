package co.tecnosport.api.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import co.tecnosport.api.domain.catalogo.MuestraDeColor;
import co.tecnosport.api.domain.catalogo.ParteDeMuestra;
import co.tecnosport.api.domain.catalogo.PatronDeColor;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodecDeMuestraTest {

  @Test
  void unaCombinacionConPatronVaYVuelve() {
    MuestraDeColor muestra =
        new MuestraDeColor(
            List.of(
                new ParteDeMuestra(PatronDeColor.ANIMAL_PRINT, List.of("#C19A6B", "#3B2A1A")),
                ParteDeMuestra.lisa("#111111")));

    String texto = CodecDeMuestra.aTexto(muestra);

    assertThat(texto).isEqualTo("ANIMAL_PRINT:#C19A6B,#3B2A1A;#111111");
    assertThat(CodecDeMuestra.deTexto(texto, "#C19A6B")).isEqualTo(muestra);
  }

  /** Una fila de antes de V80 solo tiene `color_hex`: su muestra es ese color, liso. */
  @Test
  void sinMuestraGuardadaEsElColorHexSolo() {
    assertThat(CodecDeMuestra.deTexto(null, "#1E3A8A")).isEqualTo(MuestraDeColor.lisa("#1E3A8A"));
    assertThat(CodecDeMuestra.deTexto(null, null)).isNull();
    assertThat(CodecDeMuestra.aTexto(null)).isNull();
  }
}
