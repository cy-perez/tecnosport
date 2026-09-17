package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.BandejaDeRevision;
import co.tecnosport.api.application.envio.EmisionEnRevision;
import co.tecnosport.api.application.envio.GuiaEnRevision;
import co.tecnosport.api.domain.envio.AcuseDeRevision;
import co.tecnosport.api.presentation.envio.dto.AcuseDeRevisionRespuesta;
import co.tecnosport.api.presentation.envio.dto.BandejaDeRevisionRespuesta;
import co.tecnosport.api.presentation.envio.dto.BandejaDeRevisionRespuesta.EmisionEnRevisionRespuesta;
import co.tecnosport.api.presentation.envio.dto.BandejaDeRevisionRespuesta.GuiaEnRevisionRespuesta;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Los enumerados salen por su nombre; traducirlos es de la pantalla (regla dura #4). */
@Component
public class MapeadorBandejaDeRevision {

  public BandejaDeRevisionRespuesta aRespuesta(BandejaDeRevision bandeja) {
    return new BandejaDeRevisionRespuesta(
        bandeja.guias().stream().map(MapeadorBandejaDeRevision::aRespuesta).toList(),
        bandeja.emisiones().stream().map(MapeadorBandejaDeRevision::aRespuesta).toList());
  }

  private static GuiaEnRevisionRespuesta aRespuesta(GuiaEnRevision guia) {
    return new GuiaEnRevisionRespuesta(
        guia.guiaId().toString(),
        guia.numeroGuia(),
        guia.transportadora(),
        guia.pedidoId().toString(),
        guia.numeroPedido(),
        guia.estado().name(),
        guia.descripcion(),
        guia.ocurrioEn(),
        guia.recibidoEn(),
        guia.revisadaEn());
  }

  private static EmisionEnRevisionRespuesta aRespuesta(EmisionEnRevision emision) {
    return new EmisionEnRevisionRespuesta(
        emision.emisionId().toString(),
        emision.pedidoId().toString(),
        emision.numeroPedido(),
        emision.transportadora(),
        emision.idTarifa(),
        emision.estado().name(),
        emision.detalle(),
        emision.enviosEnPlataforma(),
        emision.solicitadaEn(),
        emision.actor());
  }

  public AcuseDeRevisionRespuesta aRespuesta(AcuseDeRevision acuse) {
    Objects.requireNonNull(acuse);
    return new AcuseDeRevisionRespuesta(
        acuse.id().toString(),
        acuse.tipo().name(),
        acuse.referencia().toString(),
        acuse.revisadoEn(),
        acuse.actor(),
        acuse.nota().orElse(null));
  }
}
