package co.tecnosport.api.presentation.envio;

import co.tecnosport.api.application.pedido.ModalidadesDeEntrega;
import co.tecnosport.api.presentation.envio.dto.ModalidadesDeEntregaRespuesta;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Público: el checkout lo lee antes de ofrecer el tipo de entrega. Es una lectura de configuración,
 * no una decisión: quien la toma es {@code CrearPedido}, que rechaza la recogida apagada aunque un
 * cliente la mande igual.
 */
@RestController
@RequestMapping("/api/v1/envios/modalidades")
public class ModalidadesDeEntregaControlador {

  private final ModalidadesDeEntrega modalidadesDeEntrega;

  public ModalidadesDeEntregaControlador(ModalidadesDeEntrega modalidadesDeEntrega) {
    this.modalidadesDeEntrega = Objects.requireNonNull(modalidadesDeEntrega);
  }

  @GetMapping
  public ModalidadesDeEntregaRespuesta consultar() {
    return new ModalidadesDeEntregaRespuesta(true, modalidadesDeEntrega.retiroEnPunto());
  }
}
