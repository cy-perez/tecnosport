package co.tecnosport.api.application.sugerencia;

import co.tecnosport.api.application.compartido.CorreoNoEnviadoException;
import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.LimitadorDeIntentos;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.application.legal.RepositorioAutorizaciones;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.legal.AutorizacionDatos;
import co.tecnosport.api.domain.sugerencia.Sugerencia;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Recibe una sugerencia del buzón público, la guarda y avisa al correo del negocio.
 *
 * <h2>Con correo o sin él</h2>
 *
 * <p>Sin correo, esto no trata ningún dato personal: guarda un texto y una fecha. Con correo, entra
 * la Ley 1581 completa, y entonces el sí es obligatorio y queda su constancia con la versión de la
 * política —que <b>la fija el servidor</b>, nunca el navegador— y la IP. Es la misma mecánica del
 * registro y del checkout, con una diferencia: aquí nunca hay usuario detrás.
 *
 * <p>El orden importa y es el mismo que en {@code RegistrarUsuario}: la autorización se exige
 * <b>antes</b> de escribir nada. Guardar primero y validar después dejaría el dato de una persona
 * que no consintió dentro de la base durante el tiempo que tarde en reventar la transacción, que es
 * exactamente lo que la constancia existe para impedir.
 *
 * <h2>El límite</h2>
 *
 * <p>Por correo, y vale poco por sí solo: un buzón público que admite anónimos no puede identificar
 * a quien insiste. Lo que de verdad aguanta el aluvión es el límite por IP del filtro que {@code
 * ConfiguracionLimiteIntentos} engancha a esta ruta. Este segundo límite protege otra cosa: que
 * alguien use el buzón para hostigar una casilla ajena, escribiendo con el correo de otro para que
 * le lleguen los acuses. El mismo motivo por el que {@code SolicitarRecuperacion} cuenta por correo
 * antes de mirar si ese correo existe.
 *
 * <p>Y hay que decirlo claro, porque ya está anotado en el proyecto: <b>los dos son evadibles</b>.
 * La IP la puede cambiar quien quiera y el correo se escribe a mano. Frenan el ruido accidental y
 * el abuso perezoso; no frenan a quien se lo proponga. Lo que hay detrás es el tope de dos mil
 * caracteres y que esto no publica nada en ninguna parte — una sugerencia va a una tabla y a un
 * correo interno, no a la vitrina.
 *
 * <h2>El acuse no se manda</h2>
 *
 * <p>A quien escribió no se le contesta con un correo automático, aunque haya dejado el suyo. Un
 * acuse automático a una dirección que cualquiera puede teclear convierte el buzón en una máquina
 * de mandar correos a terceros, que es el abuso clásico de un formulario público. El acuse se da en
 * la pantalla, que es donde está quien acaba de escribir; la respuesta, si la hay, la escribe una
 * persona.
 */
public final class EnviarSugerencia {

  private final RepositorioSugerencias repositorioSugerencias;
  private final RepositorioAutorizaciones repositorioAutorizaciones;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final LimitadorDeIntentos limitadorDeIntentos;
  private final int maximoIntentosPorCorreo;
  private final Duration ventanaIntentosPorCorreo;
  private final String versionPolitica;
  private final CorreoElectronico destinatarioDelAviso;

  public EnviarSugerencia(
      RepositorioSugerencias repositorioSugerencias,
      RepositorioAutorizaciones repositorioAutorizaciones,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      LimitadorDeIntentos limitadorDeIntentos,
      int maximoIntentosPorCorreo,
      Duration ventanaIntentosPorCorreo,
      String versionPolitica,
      CorreoElectronico destinatarioDelAviso) {
    this.repositorioSugerencias = Objects.requireNonNull(repositorioSugerencias);
    this.repositorioAutorizaciones = Objects.requireNonNull(repositorioAutorizaciones);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.limitadorDeIntentos = Objects.requireNonNull(limitadorDeIntentos);
    this.maximoIntentosPorCorreo = maximoIntentosPorCorreo;
    this.ventanaIntentosPorCorreo = Objects.requireNonNull(ventanaIntentosPorCorreo);
    this.versionPolitica = Objects.requireNonNull(versionPolitica);
    this.destinatarioDelAviso = Objects.requireNonNull(destinatarioDelAviso);
  }

  public void ejecutar(EnviarSugerenciaComando comando) {
    Objects.requireNonNull(comando, "El comando no puede ser nulo.");

    Instant ahora = reloj.ahora();
    CorreoElectronico correo = correoDe(comando);

    if (correo != null) {
      if (!limitadorDeIntentos.permitir(
          "cuenta:enviar-sugerencia:" + correo.valor(),
          maximoIntentosPorCorreo,
          ventanaIntentosPorCorreo,
          ahora)) {
        throw new LimiteDeIntentosExcedidoException();
      }
      // Antes de construir nada: sin el sí no se guarda el correo de nadie.
      AutorizacionDatos.exigirAutorizacion(comando.autorizaDatos());
    }

    // El agregado valida el mensaje —vacío no, y con tope—, así que un mensaje malo revienta aquí
    // y no después de haber contado un intento de más contra el correo de quien escribe bien.
    Sugerencia sugerencia = Sugerencia.recibir(comando.mensaje(), correo, ahora);

    repositorioSugerencias.guardar(sugerencia);

    if (correo != null) {
      repositorioAutorizaciones.guardar(
          AutorizacionDatos.enSugerencia(
              comando.autorizaDatos(), correo, versionPolitica, comando.direccionIp(), ahora));
    }

    avisarAlNegocio(sugerencia);
  }

  /**
   * El aviso interno. Se traga el fallo a propósito, que es lo contrario de lo que hacen los
   * correos del comprador: la sugerencia <b>ya está guardada</b>, y tumbar la transacción por no
   * poder encolar un aviso devolvería un error a quien acaba de escribirnos —y perdería su texto—
   * para proteger una notificación que el propio repositorio ya hace innecesaria. Lo que se pierde
   * es enterarse rápido, y eso queda en el registro.
   */
  private void avisarAlNegocio(Sugerencia sugerencia) {
    String remitente =
        sugerencia
            .correo()
            .map(CorreoElectronico::valor)
            .orElseGet(() -> textos.texto(TextoDeCorreo.SUGERENCIA_ANONIMA));
    try {
      enviadorDeCorreo.enviar(
          destinatarioDelAviso,
          textos.texto(TextoDeCorreo.SUGERENCIA_ASUNTO),
          textos.texto(TextoDeCorreo.SUGERENCIA_CUERPO, remitente, sugerencia.mensaje()));
    } catch (CorreoNoEnviadoException registradoPorElAdaptador) {
      // No se relanza, y no se registra aqui: `application` no declara ninguna dependencia --ni
      // siquiera un registrador-- y quien deja constancia del fallo es el adaptador de correo,
      // igual que en `AvisarSaldoBajo`. Lo que se pierde es enterarse rapido; la sugerencia ya
      // esta guardada.
    }
  }

  /** {@code null} si quien escribió prefirió no dejarlo. */
  private CorreoElectronico correoDe(EnviarSugerenciaComando comando) {
    String valor = comando.correo();
    return valor == null || valor.isBlank() ? null : new CorreoElectronico(valor);
  }
}
