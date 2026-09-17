package co.tecnosport.api.application.envio;

import co.tecnosport.api.application.compartido.EnviadorDeCorreo;
import co.tecnosport.api.application.compartido.Reloj;
import co.tecnosport.api.application.compartido.TextoDeCorreo;
import co.tecnosport.api.application.compartido.TextosDeCorreo;
import co.tecnosport.api.domain.compartido.CorreoElectronico;
import co.tecnosport.api.domain.envio.TipoDeRevision;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Avisa al negocio cuando algo lleva demasiado tiempo en la bandeja de revisión sin que nadie lo
 * mire.
 *
 * <p>Existe porque <strong>que la pantalla exista no hace que alguien la abra</strong>. La bandeja
 * resolvió que los estados que piden ojo humano se puedan ver; esto resuelve que alguien se entere
 * sin tener que acordarse de mirar. Es el mismo razonamiento del vigilante del plazo de entrega
 * (adr/0028): un dato que solo vive en una pantalla no lo atiende nadie.
 *
 * <p><strong>No resuelve ni acusa nada.</strong> Avisar no cuenta como revisar: lo que está quieto
 * sigue quieto y sigue en la bandeja hasta que una persona lo mire. Por eso el aviso se guarda en
 * su propia tabla y no como un acuse — un acuse del sistema vaciaría la bandeja sin que nadie
 * hubiera mirado nada, que es exactamente el defecto que todo esto vino a corregir.
 *
 * <p>Un solo correo con todo lo que hay, y no uno por fila: el destinatario es el negocio, y diez
 * correos seguidos se leen igual de mal que ninguno.
 */
public final class AvisarRevisionPendiente {

  private final ListarEnviosEnRevision bandeja;
  private final RepositorioAvisosDeRevision avisos;
  private final EnviadorDeCorreo enviadorDeCorreo;
  private final TextosDeCorreo textos;
  private final Reloj reloj;
  private final Duration umbral;
  private final CorreoElectronico destinatario;
  private final int maximo;

  public AvisarRevisionPendiente(
      ListarEnviosEnRevision bandeja,
      RepositorioAvisosDeRevision avisos,
      EnviadorDeCorreo enviadorDeCorreo,
      TextosDeCorreo textos,
      Reloj reloj,
      Duration umbral,
      CorreoElectronico destinatario,
      int maximo) {
    this.bandeja = Objects.requireNonNull(bandeja);
    this.avisos = Objects.requireNonNull(avisos);
    this.enviadorDeCorreo = Objects.requireNonNull(enviadorDeCorreo);
    this.textos = Objects.requireNonNull(textos);
    this.reloj = Objects.requireNonNull(reloj);
    this.umbral = Objects.requireNonNull(umbral);
    this.destinatario = Objects.requireNonNull(destinatario);
    this.maximo = maximo;
  }

  public ResultadoVigilanciaRevision ejecutar() {
    Instant ahora = reloj.ahora();
    Instant corte = ahora.minus(umbral);
    BandejaDeRevision pendiente = bandeja.ejecutar(maximo);

    List<GuiaEnRevision> guiasVencidas =
        pendiente.guias().stream().filter(guia -> guia.recibidoEn().isBefore(corte)).toList();
    List<EmisionEnRevision> emisionesVencidas =
        pendiente.emisiones().stream()
            .filter(emision -> emision.solicitadaEn().isBefore(corte))
            .toList();

    List<String> lineas = new ArrayList<>();
    for (GuiaEnRevision guia : guiasVencidas) {
      // Reclamar antes de escribir el correo, y solo incluir lo que se ganó: el que pierde es otra
      // instancia que ya avisó de esto mismo.
      if (avisos.reclamarAviso(TipoDeRevision.GUIA, guia.guiaId(), guia.recibidoEn(), ahora)) {
        lineas.add(
            textos.texto(
                TextoDeCorreo.ENVIO_REVISION_PENDIENTE_GUIA,
                guia.numeroGuia(),
                guia.numeroPedido(),
                guia.estado().name(),
                guia.recibidoEn()));
      }
    }
    for (EmisionEnRevision emision : emisionesVencidas) {
      if (avisos.reclamarAviso(
          TipoDeRevision.EMISION, emision.emisionId(), emision.solicitadaEn(), ahora)) {
        lineas.add(
            textos.texto(
                TextoDeCorreo.ENVIO_REVISION_PENDIENTE_EMISION,
                emision.numeroPedido(),
                emision.transportadora(),
                emision.estado().name(),
                emision.idTarifa()));
      }
    }

    int vencidas = guiasVencidas.size() + emisionesVencidas.size();
    if (lineas.isEmpty()) {
      return new ResultadoVigilanciaRevision(vencidas, 0);
    }
    avisar(lineas);
    return new ResultadoVigilanciaRevision(vencidas, lineas.size());
  }

  /**
   * El envío va después de los reclamos, nunca antes: mismo criterio que el vigilante del plazo de
   * entrega. Un fallo aquí deja las marcas puestas y el correo sin salir, que es el lado por el que
   * se prefiere fallar — lo que está quieto sigue en la bandeja, visible, y el adaptador de
   * producción se traga los fallos de envío de todas formas.
   *
   * <p>Los estados viajan por su nombre del enum y no traducidos, al revés que en la pantalla: este
   * correo lo lee quien opera, y {@code RETENIDO} es la misma palabra que va a ver en el panel de
   * la transportadora y en el registro. Traducirlo aquí daría dos nombres para lo mismo.
   */
  private void avisar(List<String> lineas) {
    String cuerpo =
        textos.texto(TextoDeCorreo.ENVIO_REVISION_PENDIENTE_CUERPO, umbral.toHours(), lineas.size())
            + String.join("", lineas)
            + textos.texto(TextoDeCorreo.ENVIO_REVISION_PENDIENTE_CIERRE);
    enviadorDeCorreo.enviar(
        destinatario,
        textos.texto(TextoDeCorreo.ENVIO_REVISION_PENDIENTE_ASUNTO, lineas.size()),
        cuerpo);
  }
}
