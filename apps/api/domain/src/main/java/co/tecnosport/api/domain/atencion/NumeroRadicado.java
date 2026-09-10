package co.tecnosport.api.domain.atencion;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import java.util.regex.Pattern;

/**
 * El número con el que el interesado puede volver a preguntar por lo que radicó ({@code
 * TS-PQR-2026-000123}), distinto del {@code id} interno.
 *
 * <p>Existe porque los términos publicados no prometen "recibir" las peticiones sino
 * <b>radicarlas</b>, y radicar sin número no es radicar: quien escribe no tiene después cómo
 * referirse a lo que pidió, ni con qué demostrar la fecha en que lo pidió.
 *
 * <p>El dominio no lo genera solo, mismo motivo que {@code NumeroPedido}: el secuencial exige
 * atomicidad entre transacciones concurrentes, y eso solo lo da la base de datos.
 */
public record NumeroRadicado(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^TS-PQR-\\d{4}-\\d{6}$");
  private static final long SECUENCIAL_MINIMO = 1;
  private static final long SECUENCIAL_MAXIMO = 999_999;

  public NumeroRadicado {
    if (valor == null || !FORMATO.matcher(valor).matches()) {
      throw new ExcepcionDeDominio(
          "El número de radicado \"" + valor + "\" no tiene el formato TS-PQR-AAAA-NNNNNN.");
    }
  }

  public static NumeroRadicado de(int anio, long secuencial) {
    if (secuencial < SECUENCIAL_MINIMO || secuencial > SECUENCIAL_MAXIMO) {
      throw new ExcepcionDeDominio(
          "El secuencial "
              + secuencial
              + " está fuera de rango ("
              + SECUENCIAL_MINIMO
              + " a "
              + SECUENCIAL_MAXIMO
              + ").");
    }
    return new NumeroRadicado(String.format("TS-PQR-%04d-%06d", anio, secuencial));
  }
}
