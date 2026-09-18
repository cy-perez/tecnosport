package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.compartido.Dinero;
import java.util.Objects;
import java.util.Optional;

/**
 * Avisa al negocio cuando el crédito de la plataforma de envíos se está acabando.
 *
 * <p>Es el hermano pequeño de {@link AvisarRevisionPendiente} y nace del mismo defecto, un paso más
 * atrás: aquél avisa de un paquete que se quedó quieto, y esto avisa de que <strong>ninguno va a
 * poder salir</strong>. El endpoint del saldo existe desde el 14 de septiembre de 2026
 * (docs/13-skydropx-capacidades.md §6.2) y no lo usaba nadie; mientras tanto la cuenta llegó a
 * quedarse en COP 388, y la forma de enterarse fue que la emisión de un pedido pagado no salió.
 *
 * <p><strong>Sin tabla y sin memoria de lo ya avisado</strong>, a diferencia del aviso de la
 * bandeja. Aquél necesita recordar porque su aviso habla de filas concretas que siguen ahí; este
 * habla de un único número, y repetirlo mientras siga bajo <em>es</em> el mensaje: la cuenta sigue
 * sin plata. Lo que evita que se vuelva ruido es el intervalo, no un registro — y una tabla cuyo
 * único trabajo fuera callar una advertencia de dinero sería una tabla mal puesta.
 *
 * <p>Que no se pueda preguntar no es una alarma. {@link ConsultorDeSaldo} devuelve vacío cuando la
 * plataforma no contesta, y eso aquí no manda ningún correo: un proveedor caído media hora no es
 * una cuenta sin fondos, y confundirlos enseña a ignorar el aviso.
 */
public final class AvisarSaldoBajo {

  private final ConsultorDeSaldo consultorDeSaldo;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Dinero umbral;
  private final CorreoElectronico destinatario;

  public AvisarSaldoBajo(
      ConsultorDeSaldo consultorDeSaldo,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Dinero umbral,
      CorreoElectronico destinatario) {
    this.consultorDeSaldo = Objects.requireNonNull(consultorDeSaldo);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.umbral = Objects.requireNonNull(umbral);
    this.destinatario = Objects.requireNonNull(destinatario);
  }

  public ResultadoVigilanciaSaldo ejecutar() {
    Optional<Dinero> saldo = consultorDeSaldo.saldo();
    if (saldo.isEmpty()) {
      return ResultadoVigilanciaSaldo.noSeSabe();
    }
    Dinero actual = saldo.get();
    if (actual.valor().compareTo(umbral.valor()) >= 0) {
      return ResultadoVigilanciaSaldo.suficiente(actual);
    }
    enviadorDeCorreo.enviar(
        destinatario,
        textos.texto(TextoDeCorreo.ENVIO_SALDO_BAJO_ASUNTO, actual.valor().toPlainString()),
        textos.texto(
            TextoDeCorreo.ENVIO_SALDO_BAJO_CUERPO,
            actual.valor().toPlainString(),
            umbral.valor().toPlainString()));
    return ResultadoVigilanciaSaldo.avisado(actual);
  }
}
