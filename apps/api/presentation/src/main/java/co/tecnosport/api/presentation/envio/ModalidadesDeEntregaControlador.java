package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.pedido.ModalidadesDeEntrega;
import co.tecnosport.api.application.pedido.RecogidaEnPunto;
import co.tecnosport.api.presentation.envio.dto.ModalidadesDeEntregaRequest;
import co.tecnosport.api.presentation.envio.dto.ModalidadesDeEntregaRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Público: el checkout lo lee antes de ofrecer el tipo de entrega. Es una pregunta, no una
 * decisión: quien la toma es {@code CrearPedido}, que rechaza la recogida aunque un cliente la
 * mande igual.
 *
 * <p>Es {@code POST} y no {@code GET} desde el 26 de octubre de 2026 porque la respuesta depende
 * del carrito: la recogida se ofrece solo si todo lo que lleva viene de proveedores locales
 * (ADR-0093). Es una lectura de la base, sin terceros, pero cuántas lecturas hace lo decide quien
 * llama, así que va bajo el límite por IP de la cotización y {@code RecogidaEnPunto} le pone tope a
 * la lista.
 */
@RestController
@RequestMapping("/api/v1/envios/modalidades")
public class ModalidadesDeEntregaControlador {

  private final ModalidadesDeEntrega modalidadesDeEntrega;
  private final RecogidaEnPunto recogidaEnPunto;

  public ModalidadesDeEntregaControlador(
      ModalidadesDeEntrega modalidadesDeEntrega, RecogidaEnPunto recogidaEnPunto) {
    this.modalidadesDeEntrega = Objects.requireNonNull(modalidadesDeEntrega);
    this.recogidaEnPunto = Objects.requireNonNull(recogidaEnPunto);
  }

  @PostMapping
  public ModalidadesDeEntregaRespuesta consultarModalidades(
      @RequestBody ModalidadesDeEntregaRequest cuerpo) {
    return new ModalidadesDeEntregaRespuesta(
        modalidadesDeEntrega.envioADomicilio(),
        recogidaEnPunto.disponiblePara(cuerpo.varianteIds()));
  }
}
