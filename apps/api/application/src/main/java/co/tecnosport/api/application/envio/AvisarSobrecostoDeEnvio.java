package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Avisa al negocio cuando la transportadora cobra más de lo que la tarifa decía.
 *
 * <p>Es el tercer vigilante de este módulo y el que mira el dinero que ya se fue. {@link
 * AvisarRevisionPendiente} avisa de un paquete quieto; {@link AvisarSaldoBajo}, de que no se va a
 * poder emitir; esto avisa de que <strong>un despacho costó más de lo que el pedido dice que
 * costó</strong>. El endpoint existe desde antes de la primera guía y no lo miraba nadie ({@code
 * docs/13-skydropx-capacidades.md} §2.5): el sobrecosto se descuenta del crédito, y el margen del
 * pedido se queda diciendo el flete de la tarifa.
 *
 * <p><strong>Sin umbral: se avisa de todos.</strong> Un cobro extra es raro y cada uno se come
 * saldo en silencio, así que no hay una cifra por debajo de la cual convenga callar — y ponerla
 * sería inventar un dato de negocio que nadie ha decidido. Lo que evita el ruido es que cada cobro
 * se avisa una sola vez.
 *
 * <p><strong>Una ventana y no "desde siempre".</strong> Se pregunta por los cobros detectados en
 * los últimos días porque el orden en que la plataforma los devuelve no está documentado, así que
 * pedir "los recientes" con paginación sería apoyarse en algo que nadie prometió. Volver a leer la
 * misma ventana no cuesta nada: de lo ya avisado no se avisa dos veces.
 *
 * <p>Un solo correo con todo lo nuevo, y no uno por cobro: mismo criterio que la bandeja — el
 * destinatario es el negocio, y cinco correos seguidos se leen igual de mal que ninguno.
 */
public final class AvisarSobrecostoDeEnvio {

  private final ConsultorDeSobrecostos consultor;
  private final RepositorioAvisosDeSobrecosto avisos;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration ventana;
  private final CorreoElectronico destinatario;

  public AvisarSobrecostoDeEnvio(
      ConsultorDeSobrecostos consultor,
      RepositorioAvisosDeSobrecosto avisos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration ventana,
      CorreoElectronico destinatario) {
    this.consultor = Objects.requireNonNull(consultor);
    this.avisos = Objects.requireNonNull(avisos);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.ventana = Objects.requireNonNull(ventana);
    this.destinatario = Objects.requireNonNull(destinatario);
  }

  public ResultadoVigilanciaSobrecostos ejecutar() {
    Instant ahora = reloj.ahora();
    Optional<List<SobrecostoDeEnvio>> respuesta = consultor.desde(ahora.minus(ventana));
    if (respuesta.isEmpty()) {
      return ResultadoVigilanciaSobrecostos.noSeSabe();
    }

    List<SobrecostoDeEnvio> cobros = respuesta.get();
    List<String> lineas = new ArrayList<>();
    for (SobrecostoDeEnvio cobro : cobros) {
      // Reclamar antes de escribir la línea, y solo incluir lo que se ganó: el que pierde es otra
      // vuelta —u otra instancia— que ya avisó de este mismo cobro.
      if (avisos.reclamarAviso(cobro.clave(), ahora)) {
        lineas.add(linea(cobro));
      }
    }

    if (lineas.isEmpty()) {
      return ResultadoVigilanciaSobrecostos.sinNovedad(cobros.size());
    }
    avisar(lineas);
    return ResultadoVigilanciaSobrecostos.avisado(cobros.size(), lineas.size());
  }

  /**
   * El tipo del cargo viaja tal como lo manda la plataforma —{@code ExtraCharge::Overweight}— y no
   * traducido. Es feo y es deliberado: este correo lo lee quien opera, y esa es la misma cadena que
   * va a ver en el panel de Skydropx cuando vaya a buscar el cargo. Traducirlo daría dos nombres
   * para lo mismo, que es el problema que el rastreo ya tuvo con el código de la transportadora.
   */
  private String linea(SobrecostoDeEnvio cobro) {
    return textos.texto(
        TextoDeCorreo.ENVIO_SOBRECOSTO_COBRO,
        cobro.monto().valor().toPlainString(),
        cobro.tipo(),
        cobro.numeroDeGuia().orElseGet(() -> textos.texto(TextoDeCorreo.ENVIO_SOBRECOSTO_SIN_GUIA)),
        cobro.nombreDeTransportadora().orElse("—"),
        cobro
            .detectado()
            .map(Instant::toString)
            .orElseGet(() -> textos.texto(TextoDeCorreo.ENVIO_SOBRECOSTO_SIN_FECHA)),
        cobro.envioEnPlataforma());
  }

  /**
   * El correo sale después de los reclamos, nunca antes: mismo criterio que los otros dos
   * vigilantes. Un fallo aquí deja las marcas puestas y el correo sin salir — se prefiere ese lado
   * porque el dato no se pierde (sigue en la plataforma, y el saldo ya bajó) y porque el adaptador
   * de producción se traga los fallos de envío de todas formas.
   */
  private void avisar(List<String> lineas) {
    String cuerpo =
        textos.texto(TextoDeCorreo.ENVIO_SOBRECOSTO_CUERPO, lineas.size())
            + String.join("", lineas)
            + textos.texto(TextoDeCorreo.ENVIO_SOBRECOSTO_CIERRE);
    enviadorDeCorreo.enviar(
        destinatario, textos.texto(TextoDeCorreo.ENVIO_SOBRECOSTO_ASUNTO, lineas.size()), cuerpo);
  }
}
