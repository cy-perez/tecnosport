package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.CotizarEnvio;
import co.tecnosport.api.application.envio.CotizarEnvioComando;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.domain.envio.TarifaEnvio;
import co.tecnosport.api.domain.pedido.Direccion;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.envio.dto.CotizacionEnvioRequest;
import co.tecnosport.api.presentation.envio.dto.CotizacionEnvioRespuesta;
import co.tecnosport.api.presentation.pedido.dto.CrearPedidoRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Público: el checkout cotiza antes de que exista un pedido y sin exigir sesión.
 *
 * <p>{@code RETIRO_EN_PUNTO} no pasa por aquí. No hay destino que cotizar y el costo es cero por
 * definición, así que el checkout simplemente no llama.
 */
@RestController
@RequestMapping("/api/v1/envios/cotizacion")
public class CotizacionEnvioControlador {

  private final CotizarEnvio cotizarEnvio;

  public CotizacionEnvioControlador(CotizarEnvio cotizarEnvio) {
    this.cotizarEnvio = Objects.requireNonNull(cotizarEnvio);
  }

  @PostMapping
  public CotizacionEnvioRespuesta cotizar(@RequestBody CotizacionEnvioRequest cuerpo) {
    TarifaEnvio tarifa = cotizarEnvio.ejecutar(aComando(cuerpo));
    return new CotizacionEnvioRespuesta(
        aDinero(tarifa.costo()), tarifa.transportadora(), tarifa.diasEstimados(), tarifa.venceEn());
  }

  private CotizarEnvioComando aComando(CotizacionEnvioRequest cuerpo) {
    List<CotizarEnvioComando.LineaComando> lineas =
        cuerpo.lineas().stream()
            .map(
                linea -> new CotizarEnvioComando.LineaComando(linea.varianteId(), linea.cantidad()))
            .toList();
    return new CotizarEnvioComando(lineas, aDireccion(cuerpo.direccion()));
  }

  private Direccion aDireccion(CrearPedidoRequest.DireccionRequest d) {
    return new Direccion(
        d.codigoDaneDepartamento(),
        d.departamento(),
        d.codigoDaneCiudad(),
        d.ciudad(),
        d.direccion(),
        d.indicaciones());
  }

  private DineroRespuesta aDinero(Dinero dinero) {
    return new DineroRespuesta(dinero.valor().longValueExact(), Dinero.MONEDA);
  }
}
