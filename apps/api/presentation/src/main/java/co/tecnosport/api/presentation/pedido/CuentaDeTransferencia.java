package co.tecnosport.api.presentation.pedido;

/**
 * Una cuenta a la que el comprador puede transferir (docs/11-pagos-y-envios.md).
 *
 * <p><b>{@code entidad} y no {@code banco}</b>, que es como se llamaba cuando solo había una: dos
 * de las tres no son bancos sino billeteras —Nequi y Daviplata— y llamarlas banco obligaba a
 * escribir "Bancolombia" donde el comprador espera leer "Nequi". El campo dice lo que el comprador
 * tiene que buscar en su app, que es lo único que importa aquí.
 *
 * <p><b>{@code tipo} es texto y no un enum</b> a propósito. Lo que hace es decirle a quien
 * transfiere qué va a encontrar —"ahorros", "billetera"— y el día que entre una corriente o un
 * convenio de recaudo, el valor nuevo no debería exigir una migración: no se persiste, se muestra.
 * Lo que sí está cerrado es el conjunto de cuentas, que vive en {@code application.yml}.
 *
 * <p>Los valores por defecto de {@code numero} y {@code titular} son placeholders que <b>nunca
 * sirven para transferir de verdad</b> (regla dura #5): el dato real entra por variable de entorno.
 * Y el titular es el nombre de quien tiene la cuenta, <b>no el nombre comercial</b> — el sitio lo
 * opera una persona natural (docs/08-seguridad-legal.md).
 */
public record CuentaDeTransferencia(String entidad, String tipo, String numero, String titular) {

  public CuentaDeTransferencia {
    exigir(entidad, "entidad");
    exigir(tipo, "tipo");
    exigir(numero, "numero");
    exigir(titular, "titular");
  }

  private static void exigir(String valor, String campo) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(
          "tecnosport.transferencia-manual.cuentas[]." + campo + " no puede estar vacío.");
    }
  }
}
