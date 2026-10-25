package co.tecnosport.api.application.pedido;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.CorreoElectronicoInvalidoException;
import co.tecnosport.api.domain.pedido.NumeroPedido;
import co.tecnosport.api.domain.pedido.NumeroPedidoInvalidoException;
import co.tecnosport.api.domain.pedido.Pedido;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * El formulario de "Estado del pedido": con el número legible y el correo, le manda a ese correo el
 * enlace a la pantalla de estado. <b>No devuelve el pedido ni dice si existe</b> (ADR-0088).
 *
 * <h2>Por qué ya no se consulta aquí mismo</h2>
 *
 * <p>Hasta el 25 de octubre de 2026 esto era {@code ConsultarSeguimientoPorNumero}, y devolvía el
 * pedido cuando el número y el correo coincidían. El número va del {@code 000001} hacia arriba, así
 * que quien conociera el correo de una persona podía recorrer números hasta dar con un pedido suyo
 * y ver qué compró, a dónde se lo llevan y cuándo. El límite por IP lo encarecía; no lo cerraba, y
 * además se podía evadir (ADR-0087).
 *
 * <p>Ahora quien acierta no ve nada: el enlace llega al buzón del titular. Es el mismo enlace del
 * comprobante y del correo de despacho, que entra por el id —un UUID v7— y no por el número.
 *
 * <h2>Lo que responde</h2>
 *
 * <p>Nada, en todos los casos: número mal escrito, correo con formato imposible, número que no
 * existe, correo que no coincide, tope alcanzado y correo enviado. Cualquier diferencia entre ellos
 * sería un oráculo para quien prueba a ciegas.
 *
 * <p>El tiempo, en cambio, sí distingue: cuando el pedido existe se carga el agregado y se escribe
 * en el limitador y en la bandeja de salida, y eso diría si un correo es de un cliente. Esta clase
 * no lo puede esconder sin dormir, así que lo esconde quien la llama: {@code PedidoControlador}
 * responde siempre después de un piso fijo.
 */
public final class SolicitarEnlaceDeSeguimiento {

  /**
   * Tres por hora para cada pedido, decidido el 25 de octubre de 2026. Quien pide su enlace lo pide
   * una vez, o dos si no lo encuentra; el tope impide que quien conoce el número y el correo de
   * otra persona le llene el buzón.
   */
  static final int MAXIMO_POR_PEDIDO = 3;

  static final Duration VENTANA = Duration.ofHours(1);

  private final RepositorioPedidos repositorioPedidos;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final LimitadorDeIntentos limitador;
  private final Reloj reloj;
  private final String urlBaseEstado;

  public SolicitarEnlaceDeSeguimiento(
      RepositorioPedidos repositorioPedidos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      LimitadorDeIntentos limitador,
      Reloj reloj,
      String urlBaseEstado) {
    this.repositorioPedidos = Objects.requireNonNull(repositorioPedidos);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.limitador = Objects.requireNonNull(limitador);
    this.reloj = Objects.requireNonNull(reloj);
    this.urlBaseEstado = Objects.requireNonNull(urlBaseEstado);
  }

  public void ejecutar(SolicitarEnlaceDeSeguimientoComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");
    Optional<NumeroPedido> numero = aNumero(comando.numeroPedido());
    Optional<String> correo = normalizarCorreo(comando.correo());
    if (numero.isEmpty() || correo.isEmpty()) {
      return;
    }
    // Una sola consulta con los dos criterios, y no "busca por número y compara el correo
    // después": lo segundo reconstruía el agregado antes de saber si el correo coincidía, y el
    // reloj decía cuáles números existen.
    repositorioPedidos
        .buscarPorNumeroYCorreo(numero.get(), correo.get())
        .filter(this::quedanEnviosEnLaVentana)
        .ifPresent(this::enviar);
  }

  /** Por pedido y no por IP: el límite por IP lo pone el filtro, y este protege al titular. */
  private boolean quedanEnviosEnLaVentana(Pedido pedido) {
    return limitador.permitir(
        "enlace-seguimiento:" + pedido.id(), MAXIMO_POR_PEDIDO, VENTANA, reloj.ahora());
  }

  private void enviar(Pedido pedido) {
    String numero = pedido.numeroPedido().valor();
    enviadorDeCorreo.enviar(
        pedido.correo(),
        textos.texto(TextoDeCorreo.PEDIDO_ENLACE_SEGUIMIENTO_ASUNTO, numero),
        textos.texto(
            TextoDeCorreo.PEDIDO_ENLACE_SEGUIMIENTO_CUERPO,
            numero,
            EnlaceDeEstado.de(urlBaseEstado, pedido)));
  }

  /**
   * Se acepta en minúscula y con espacios alrededor porque la gente copia y pega del correo. {@code
   * Locale.ROOT} y no el del sistema: con la configuración turca, {@code "ts-...".toUpperCase()}
   * produce {@code "TS"} con una I sin punto y el patrón deja de casar.
   */
  private static Optional<NumeroPedido> aNumero(String crudo) {
    if (crudo == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new NumeroPedido(crudo.trim().toUpperCase(Locale.ROOT)));
    } catch (NumeroPedidoInvalidoException excepcion) {
      return Optional.empty();
    }
  }

  /**
   * Con el objeto de valor que escribe el correo guardado, y no con una copia de su línea: había
   * tres y ya habían divergido en el {@code Locale.ROOT}, que es justo lo que decide si el correo
   * guardado y el buscado son el mismo.
   */
  private static Optional<String> normalizarCorreo(String crudo) {
    try {
      return Optional.of(new CorreoElectronico(crudo).valor());
    } catch (CorreoElectronicoInvalidoException excepcion) {
      return Optional.empty();
    }
  }
}
