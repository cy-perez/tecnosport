package co.tecnosport.api.domain.compartido;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Los días festivos de Colombia de un año cualquiera, calculados.
 *
 * <p>Se calculan y no se cargan de una tabla porque <b>no son un dato del negocio</b>: son una
 * regla de la ley, y la regla es determinista. Una tabla por año habría que alimentarla cada
 * diciembre, y el diciembre que nadie se acordara, tres plazos legales de este proyecto volverían a
 * responder "no se sabe" sin que ninguna prueba se quejara.
 *
 * <p><b>Ley 51 de 1983</b> (la "Ley Emiliani"), artículo 1, verificado el 10 de septiembre de 2026:
 * son festivos el 1 y el 6 de enero, el 19 de marzo, el 1 de mayo, el 29 de junio, el 20 de julio,
 * el 7 y el 15 de agosto, el 12 de octubre, el 1 y el 11 de noviembre, el 8 y el 25 de diciembre,
 * más el Jueves y el Viernes Santos, la Ascensión del Señor, el Corpus Christi y el Sagrado Corazón
 * de Jesús. De todos ellos <b>se trasladan al lunes siguiente</b>, cuando no caen en lunes, el 6 de
 * enero, el 19 de marzo, el 29 de junio, el 15 de agosto, el 12 de octubre, el 1 y el 11 de
 * noviembre, la Ascensión, el Corpus Christi y el Sagrado Corazón. Los demás se quedan donde caen,
 * y eso incluye al Jueves y al Viernes Santos, que no están en la lista del traslado.
 *
 * <p><b>Ley 2578 de 2026</b>, artículo 6, sancionada el 1 de junio de 2026: el 9 de julio, Día de
 * Nuestra Señora del Rosario de Chiquinquirá, es festivo nacional, y para fijar la fecha del
 * descanso remite a la Ley 51 de 1983 — o sea que también se traslada. Es la razón por la que un
 * calendario escrito de memoria estaría mal: Colombia pasó de dieciocho festivos a diecinueve hace
 * tres meses. Contra la ley hay una demanda de constitucionalidad en curso; mientras no haya
 * decisión, la ley rige y aquí se aplica.
 *
 * <p>Los tres días que dependen de la Pascua se cuentan desde el Domingo de Resurrección, que se
 * obtiene con el algoritmo gregoriano de Meeus. Por eso este cálculo no vale para los años
 * anteriores a la Ley 51 de 1983: allí no había traslado, y devolver un calendario que no describe
 * a ningún año real sería peor que negarse.
 */
public final class FestivosColombia {

  /** Fechas fijas que se quedan donde caen. */
  private static final List<MonthDay> FIJOS =
      List.of(
          MonthDay.of(1, 1),
          MonthDay.of(5, 1),
          MonthDay.of(7, 20),
          MonthDay.of(8, 7),
          MonthDay.of(12, 8),
          MonthDay.of(12, 25));

  /** Fechas fijas que se corren al lunes siguiente si no caen en lunes. */
  private static final List<MonthDay> TRASLADABLES =
      List.of(
          MonthDay.of(1, 6),
          MonthDay.of(3, 19),
          MonthDay.of(6, 29),
          MonthDay.of(8, 15),
          MonthDay.of(10, 12),
          MonthDay.of(11, 1),
          MonthDay.of(11, 11));

  /** Día de Nuestra Señora del Rosario de Chiquinquirá, trasladable como los demás. */
  private static final MonthDay VIRGEN_DE_CHIQUINQUIRA = MonthDay.of(7, 9);

  /** Primer año en que ese día es festivo: la Ley 2578 se sancionó el 1 de junio de 2026. */
  private static final int PRIMER_ANIO_VIRGEN_DE_CHIQUINQUIRA = 2026;

  /** Días contados desde el Domingo de Pascua, antes del traslado. */
  private static final int JUEVES_SANTO = -3;

  private static final int VIERNES_SANTO = -2;
  private static final int ASCENSION = 39;
  private static final int CORPUS_CHRISTI = 60;
  private static final int SAGRADO_CORAZON = 68;

  /** La Ley 51 de 1983 empezó a regir en 1984; antes de eso ningún festivo se trasladaba. */
  private static final int PRIMER_ANIO_CON_TRASLADO = 1984;

  private FestivosColombia() {}

  /** Los festivos de ese año, ya trasladados al lunes los que la ley traslada. */
  public static Set<LocalDate> delAnio(int anio) {
    if (anio < PRIMER_ANIO_CON_TRASLADO) {
      throw new ExcepcionDeDominio(
          "El calendario de festivos no describe los años anteriores a "
              + PRIMER_ANIO_CON_TRASLADO
              + ", cuando empezó a regir el traslado al lunes: "
              + anio);
    }
    Set<LocalDate> festivos = new HashSet<>();
    FIJOS.forEach(dia -> festivos.add(dia.atYear(anio)));
    TRASLADABLES.forEach(dia -> festivos.add(alLunesSiguiente(dia.atYear(anio))));
    if (anio >= PRIMER_ANIO_VIRGEN_DE_CHIQUINQUIRA) {
      festivos.add(alLunesSiguiente(VIRGEN_DE_CHIQUINQUIRA.atYear(anio)));
    }
    LocalDate pascua = domingoDePascua(anio);
    festivos.add(pascua.plusDays(JUEVES_SANTO));
    festivos.add(pascua.plusDays(VIERNES_SANTO));
    festivos.add(alLunesSiguiente(pascua.plusDays(ASCENSION)));
    festivos.add(alLunesSiguiente(pascua.plusDays(CORPUS_CHRISTI)));
    festivos.add(alLunesSiguiente(pascua.plusDays(SAGRADO_CORAZON)));
    return Set.copyOf(festivos);
  }

  /**
   * El Domingo de Resurrección, por el algoritmo gregoriano de Meeus.
   *
   * <p>Público a propósito: es el dato del que cuelgan cinco festivos, y una prueba que lo fije
   * contra las fechas publicadas vale más que una que compruebe los cinco por separado.
   */
  public static LocalDate domingoDePascua(int anio) {
    int a = anio % 19;
    int b = anio / 100;
    int c = anio % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int mes = (h + l - 7 * m + 114) / 31;
    int dia = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(anio, mes, dia);
  }

  private static LocalDate alLunesSiguiente(LocalDate dia) {
    return dia.getDayOfWeek() == DayOfWeek.MONDAY
        ? dia
        : dia.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
  }
}
