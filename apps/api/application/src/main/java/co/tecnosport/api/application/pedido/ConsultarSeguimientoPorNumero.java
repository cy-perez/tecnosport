package co.tecnosport.api.application.pedido;

import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.NumeroPedidoInvalidoException;
import co.tecnosport.api.domain.pedido.Pedido;
import java.util.Locale;
import java.util.Objects;

/**
 * Seguimiento sin sesión, entrando por el <b>número legible</b> del pedido y el correo.
 *
 * <p>Existe porque {@link ConsultarSeguimientoPedido} no sirve para lo que la gente necesita: pide
 * el {@code id}, que es un UUID, y el comprador nunca lo ve. El único identificador que tiene en la
 * mano es {@code TS-2026-000123}, que es el que lleva su comprobante y el que anuncia el correo de
 * despacho. Sin esto, el enlace "Estado del pedido" del pie llevaba a una pantalla que solo sabía
 * decir "no encontramos los datos del pedido".
 *
 * <p><b>Es una clase aparte y no un segundo método de la de al lado</b>, aunque las dos terminen en
 * la misma comprobación. La diferencia no es la consulta, es la seguridad: un UUID no se adivina y
 * un secuencial sí. Separarlas deja ver de un vistazo cuál de las dos necesita el límite de
 * intentos por IP —esta, y solo esta— y permite que el día que una cambie no arrastre a la otra.
 *
 * <h2>Lo que protege, y lo que no</h2>
 *
 * <p>El número es adivinable: van del {@code 000001} hacia arriba dentro de cada año. Lo que impide
 * sacar el pedido de alguien no es el número sino el <b>correo</b>, que tiene que coincidir
 * exactamente. Un correo que no coincide y un número que no existe se tratan igual —la misma
 * excepción, con el mismo mensaje— para no filtrarle a nadie si un número existe.
 *
 * <p>Eso deja un ataque en pie, y conviene decirlo en voz alta: <b>quien ya conozca el correo de
 * una persona</b> puede recorrer números hasta dar con uno suyo. Contra eso no hay nada que esta
 * clase pueda hacer, y por eso el endpoint que la expone va detrás de {@code FiltroLimiteIntentos}
 * con su propio presupuesto por IP, más estrecho que el de los demás (ver {@code
 * ConfiguracionLimiteIntentos}). Es la misma decisión que ya se tomó con {@code /auth/sesion}: el
 * secreto protege, el límite encarece intentarlo a ciegas.
 */
public final class ConsultarSeguimientoPorNumero {

  private final RepositorioPedidos repositorioPedidos;

  public ConsultarSeguimientoPorNumero(RepositorioPedidos repositorioPedidos) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
  }

  /**
   * Una sola consulta con los dos criterios, y no "busca por número y compara el correo después".
   *
   * <p>Lo segundo es lo que estaba escrito y lo levantó la revisión: comparar después obliga a
   * reconstruir el agregado entero antes de descubrir que el correo no coincide, así que un número
   * que existe tarda medible y consistentemente más que uno que no. Las dos respuestas eran el
   * mismo 404 con el mismo mensaje —y hay una prueba que lo fija— pero el reloj decía cuál era
   * cuál. Quien recorre números sin conocer el correo aprendía cuáles existen, que es justo el paso
   * previo al ataque que este caso de uso dice frenar.
   */
  public Pedido ejecutar(ConsultarSeguimientoPorNumeroComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    NumeroPedido numero = aNumero(comando.numeroPedido());
    return repositorioPedidos
        .buscarPorNumeroYCorreo(numero, normalizarCorreo(comando.correo()))
        .orElseThrow(PedidoNoEncontradoException::porSeguimiento);
  }

  /**
   * Un número mal escrito da <b>la misma</b> respuesta que uno que no existe, y no un error de
   * formato. Distinguirlos sería un oráculo gratis: quien prueba a ciegas sabría cuándo su patrón
   * es el bueno sin llegar a acertar un pedido.
   *
   * <p>Se acepta en minúscula y con espacios alrededor porque la gente copia y pega del correo.
   * {@code Locale.ROOT} y no el del sistema: con la configuración turca, {@code
   * "ts-...".toUpperCase()} produce {@code "TS"} con una I sin punto y el patrón deja de casar.
   */
  private NumeroPedido aNumero(String crudo) {
    if (crudo == null) {
      throw PedidoNoEncontradoException.porSeguimiento();
    }
    try {
      return new NumeroPedido(crudo.trim().toUpperCase(Locale.ROOT));
    } catch (NumeroPedidoInvalidoException excepcion) {
      throw PedidoNoEncontradoException.porSeguimiento();
    }
  }

  /** Igual que en {@link ConsultarSeguimientoPedido}: el correo se guarda normalizado. */
  private String normalizarCorreo(String crudo) {
    return crudo == null ? "" : crudo.trim().toLowerCase(Locale.ROOT);
  }
}
