package co.tecnosport.api.domain.compartido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Las dos listas completas están clavadas a propósito, y no comprobadas por la misma regla que las
 * genera: una prueba que recalculara el traslado comprobaría que el código coincide consigo mismo.
 * Lo que hay que sostener es que coinciden con el calendario publicado del país.
 *
 * <p>Verificadas el 10 de septiembre de 2026 contra la Ley 51 de 1983 y la Ley 2578 de 2026.
 */
class FestivosColombiaTest {

  @Test
  void losDiecinueveFestivosDe2026() {
    assertEquals(
        ordenados(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 12),
            LocalDate.of(2026, 3, 23),
            LocalDate.of(2026, 4, 2),
            LocalDate.of(2026, 4, 3),
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 18),
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 6, 29),
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 8, 7),
            LocalDate.of(2026, 8, 17),
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 16),
            LocalDate.of(2026, 12, 8),
            LocalDate.of(2026, 12, 25)),
        ordenados(FestivosColombia.delAnio(2026)));
  }

  @Test
  void losDiecinueveFestivosDe2027() {
    assertEquals(
        ordenados(
            LocalDate.of(2027, 1, 1),
            LocalDate.of(2027, 1, 11),
            LocalDate.of(2027, 3, 22),
            LocalDate.of(2027, 3, 25),
            LocalDate.of(2027, 3, 26),
            LocalDate.of(2027, 5, 1),
            LocalDate.of(2027, 5, 10),
            LocalDate.of(2027, 5, 31),
            LocalDate.of(2027, 6, 7),
            LocalDate.of(2027, 7, 5),
            LocalDate.of(2027, 7, 12),
            LocalDate.of(2027, 7, 20),
            LocalDate.of(2027, 8, 7),
            LocalDate.of(2027, 8, 16),
            LocalDate.of(2027, 10, 18),
            LocalDate.of(2027, 11, 1),
            LocalDate.of(2027, 11, 15),
            LocalDate.of(2027, 12, 8),
            LocalDate.of(2027, 12, 25)),
        ordenados(FestivosColombia.delAnio(2027)));
  }

  @Test
  void elDomingoDePascuaCoincideConLasFechasPublicadas() {
    assertEquals(LocalDate.of(2024, 3, 31), FestivosColombia.domingoDePascua(2024));
    assertEquals(LocalDate.of(2025, 4, 20), FestivosColombia.domingoDePascua(2025));
    assertEquals(LocalDate.of(2026, 4, 5), FestivosColombia.domingoDePascua(2026));
    assertEquals(LocalDate.of(2027, 3, 28), FestivosColombia.domingoDePascua(2027));
  }

  @Test
  void elJuevesYElViernesSantosNoSeTrasladan() {
    // No están en la lista del traslado de la Ley 51, y es el error más fácil de cometer al
    // escribir este calendario de memoria: los demás días de Semana Santa sí se corren.
    Set<LocalDate> festivos = FestivosColombia.delAnio(2026);

    assertTrue(festivos.contains(LocalDate.of(2026, 4, 2)));
    assertTrue(festivos.contains(LocalDate.of(2026, 4, 3)));
    assertFalse(festivos.contains(LocalDate.of(2026, 4, 6)));
  }

  @Test
  void unFestivoQueYaCaeEnLunesSeQuedaDondeEsta() {
    // El 29 de junio de 2026 es lunes: trasladarlo al "lunes siguiente" lo movería una semana.
    assertTrue(FestivosColombia.delAnio(2026).contains(LocalDate.of(2026, 6, 29)));
    assertFalse(FestivosColombia.delAnio(2026).contains(LocalDate.of(2026, 7, 6)));
  }

  @Test
  void elNueveDeJulioNoEraFestivoAntesDeLaLey2578() {
    // La ley se sancionó el 1 de junio de 2026. En 2025 el 9 de julio cayó miércoles, así que si
    // se aplicara hacia atrás aparecería el lunes 14 — y no aparece.
    Set<LocalDate> festivos2025 = FestivosColombia.delAnio(2025);

    assertFalse(festivos2025.contains(LocalDate.of(2025, 7, 9)));
    assertFalse(festivos2025.contains(LocalDate.of(2025, 7, 14)));
  }

  @Test
  void dosCelebracionesPuedenTerminarEnElMismoLunes() {
    // 2025 lo tuvo: el 29 de junio cayó domingo y el Sagrado Corazón cayó viernes 27, y las dos se
    // trasladaron al lunes 30. Son dos fiestas y un solo día de descanso, así que el conjunto las
    // colapsa con razón — pero es la explicación de por qué ese año tuvo diecisiete días festivos
    // y no dieciocho, y de por qué contar festivos por la lista de la ley da un número equivocado.
    Set<LocalDate> festivos2025 = FestivosColombia.delAnio(2025);

    assertTrue(festivos2025.contains(LocalDate.of(2025, 6, 30)));
    assertEquals(17, festivos2025.size());
  }

  @Test
  void losAniosAnterioresAlTrasladoNoSeDescriben() {
    // Antes de la Ley 51 de 1983 los festivos caían donde caían. Devolver este calendario para
    // 1983 sería describir un año que nunca existió así.
    assertThrows(ExcepcionDeDominio.class, () -> FestivosColombia.delAnio(1983));
  }

  private static Set<LocalDate> ordenados(LocalDate... dias) {
    return new TreeSet<>(Set.of(dias));
  }

  private static Set<LocalDate> ordenados(Set<LocalDate> dias) {
    return new TreeSet<>(dias);
  }
}
