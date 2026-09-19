package co.tecnosport.api.application.compartido;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Manda los correos que están esperando en la bandeja de salida, y borra los que ya salieron hace
 * bastante.
 *
 * <p><b>Por qué existe la bandeja.</b> Hasta {@code adr/0044} el correo salía dentro de la
 * transacción de quien llamaba y antes del commit, así que un fallo al comprometer dejaba a quien
 * compró leyendo "reintegramos el dinero de tu pedido" y al sistema sin ninguna constancia de ese
 * reintegro. Ningún {@code catch} arregla ese sentido: lo que hay que invertir es el orden. Ahora
 * el caso de uso escribe una fila —con su transacción, la misma que la operación de negocio— y
 * mandar el correo pasa a ser problema de esta tarea. Si la transacción revierte, la fila se va con
 * ella y el correo no existió nunca. Ver {@code adr/0045}.
 *
 * <p><b>Los cinco intentos y su espaciado son una constante del mecanismo</b>, como las 24 horas de
 * {@code RepositorioIdempotenciaJpa}: no son una tarifa ni un plazo que el negocio pueda cambiar
 * sin cambiar el mecanismo. El primero es inmediato y los cuatro reintentos se espacian 1, 5, 15 y
 * 40 minutos, o sea que el último cae a poco más de una hora del primero. Una caída corta de SMTP
 * cabe entera ahí dentro; una dirección que el servidor rechaza siempre deja de intentarse en vez
 * de dar vueltas de por vida.
 *
 * <p><b>Lo que esto no puede hacer, y hay que decirlo sin adornos:</b> avisar por correo de que los
 * correos no salen. La bandeja atascada es exactamente el estado en el que mandar un aviso es
 * imposible, así que la única señal que queda es el registro en {@code error} que escribe {@code
 * TareaBandejaDeSalida} con los rendidos de cada vuelta. Convertir ese registro en una alerta es
 * infraestructura —una política de Cloud Logging— y no vive aquí.
 */
public final class DrenarBandejaDeSalida {

  /**
   * Lo que se espera antes de cada reintento, indexado por los intentos ya hechos. Cuatro esperas
   * más el intento inmediato del principio son los cinco intentos.
   */
  private static final Duration[] ESPERAS = {
    Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(40)
  };

  /** Derivado de {@link #ESPERAS} y no escrito aparte, para que no puedan contradecirse. */
  public static final int MAX_INTENTOS = ESPERAS.length + 1;

  private final RepositorioCorreosPendientes repositorio;
  private final TransporteDeCorreo transporte;
  private final Reloj reloj;
  private final Duration retencionDeEnviados;
  private final int tamanoDelLote;

  public DrenarBandejaDeSalida(
      RepositorioCorreosPendientes repositorio,
      TransporteDeCorreo transporte,
      Reloj reloj,
      Duration retencionDeEnviados,
      int tamanoDelLote) {
    this.repositorio = Objects.requireNonNull(repositorio);
    this.transporte = Objects.requireNonNull(transporte);
    this.reloj = Objects.requireNonNull(reloj);
    this.retencionDeEnviados = Objects.requireNonNull(retencionDeEnviados);
    if (tamanoDelLote <= 0) {
      throw new IllegalArgumentException("El tamaño del lote debe ser mayor que cero.");
    }
    this.tamanoDelLote = tamanoDelLote;
  }

  public ResultadoDrenaje ejecutar() {
    Instant ahora = reloj.ahora();
    List<CorreoPendiente> lote = repositorio.buscarEnviables(MAX_INTENTOS, ahora, tamanoDelLote);

    int enviados = 0;
    int fallidos = 0;
    int rendidos = 0;
    for (CorreoPendiente correo : lote) {
      int intentoQueSigue = correo.intentos() + 1;
      // Reclamar y programar el siguiente intento son la misma sentencia: ver el javadoc del
      // puerto. Quien pierde el reclamo es otra instancia que ya está mandando este mismo correo.
      if (!repositorio.reclamar(correo.id(), ahora, ahora.plus(esperaTras(intentoQueSigue)))) {
        continue;
      }
      try {
        transporte.enviar(correo.destinatario(), correo.asunto(), correo.cuerpoHtml());
        repositorio.marcarEnviado(correo.id(), ahora);
        enviados++;
      } catch (CorreoNoEnviadoException fallo) {
        repositorio.registrarFallo(correo.id(), detalle(fallo));
        fallidos++;
        if (intentoQueSigue >= MAX_INTENTOS) {
          rendidos++;
        }
      }
    }

    int purgados = repositorio.purgarEnviados(ahora.minus(retencionDeEnviados));
    return new ResultadoDrenaje(lote.size(), enviados, fallidos, rendidos, purgados);
  }

  /**
   * La espera que sigue a un intento. Se indexa por el intento recién hecho, y el último no tiene
   * espera que le siga — se le programa la primera igualmente y da lo mismo, porque a partir de ahí
   * la consulta ya no lo trae.
   */
  private static Duration esperaTras(int intentosHechos) {
    int indice = Math.min(intentosHechos, ESPERAS.length) - 1;
    return ESPERAS[Math.max(indice, 0)];
  }

  /**
   * Lo que queda escrito en la fila. Se guarda la causa y no el mensaje de envoltorio, que es
   * siempre el mismo y no dice nada: lo útil es el "connection refused" o el "550 mailbox
   * unavailable" del servidor.
   *
   * <p>Esto va a una columna, no a un registro, y la distinción importa: docs/08-seguridad-legal.md
   * prohíbe datos personales en los <b>registros</b>, y un rechazo de SMTP suele repetir la
   * dirección. En la tabla ya está el destinatario en su propia columna, así que no se filtra nada
   * nuevo; lo que no puede pasar es que esto termine en un log, y por eso {@code
   * TareaBandejaDeSalida} solo registra contadores.
   */
  private static String detalle(CorreoNoEnviadoException fallo) {
    Throwable causa = fallo.getCause() != null ? fallo.getCause() : fallo;
    return causa.getClass().getSimpleName()
        + (causa.getMessage() == null ? "" : ": " + causa.getMessage());
  }
}
