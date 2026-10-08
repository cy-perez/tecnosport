package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.envio.BultoDespachable;
import co.tecnosport.api.application.envio.ConsultarPaquetesDePedido;
import co.tecnosport.api.application.envio.ConsultarPaquetesDePedido.PaquetesDePedido;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.domain.compartido.Dinero;
import co.tecnosport.api.presentation.compartido.dto.DineroRespuesta;
import co.tecnosport.api.presentation.envio.dto.PaquetesDePedidoRespuesta;
import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los paquetes de un pedido para crear su guía a mano ({@code adr/0071}), bajo {@code
 * /api/v1/admin/**} y protegido por rol ADMIN en {@code ConfiguracionSeguridad}.
 *
 * <p>Bajo {@code envios} y no bajo {@code pedidos}: lo que responde es cómo se empaca, que es del
 * envío; el pedido solo es la llave. Y un controlador aparte para no hacer crecer el de pedidos,
 * que ya recibe trece casos de uso.
 */
@RestController
@RequestMapping("/api/v1/admin/envios/paquetes")
public class AdminPaquetesDePedidoControlador {

  private static final int GRAMOS_POR_KILO = 1000;

  private final ConsultarPaquetesDePedido consultar;

  public AdminPaquetesDePedidoControlador(ConsultarPaquetesDePedido consultar) {
    this.consultar = Objects.requireNonNull(consultar);
  }

  @GetMapping("/{pedidoId}")
  public PaquetesDePedidoRespuesta consultarPaquetes(@PathVariable UUID pedidoId) {
    PaquetesDePedido resultado = consultar.ejecutar(pedidoId);
    return new PaquetesDePedidoRespuesta(
        resultado.paquetes().stream().map(AdminPaquetesDePedidoControlador::aRespuesta).toList(),
        resultado.conRecaudo());
  }

  /**
   * Los kilos salen exactos: el armador ya redondeó el peso hacia arriba al kilo entero, que es lo
   * único que acepta el formulario.
   */
  private static PaquetesDePedidoRespuesta.PaqueteRespuesta aRespuesta(BultoDespachable bulto) {
    Paquete paquete = bulto.bulto().paquete();
    return new PaquetesDePedidoRespuesta.PaqueteRespuesta(
        paquete.pesoGramos() / GRAMOS_POR_KILO,
        paquete.largoCm(),
        paquete.anchoCm(),
        paquete.altoCm(),
        new DineroRespuesta(bulto.bulto().valorDeclarado().valor().longValueExact(), Dinero.MONEDA),
        bulto.contenido());
  }
}
