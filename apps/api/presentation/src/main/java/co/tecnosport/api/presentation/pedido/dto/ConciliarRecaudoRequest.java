package co.tecnosport.api.presentation.pedido.dto;

import co.tecnosport.api.domain.envio.ModalidadRecaudo;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.Objects;

/**
 * {@code modalidadRecaudo} lo registra quien concilia mirando el panel de la plataforma: es por
 * dónde entró el dinero, no una instrucción que la plataforma vaya a obedecer (ver {@code
 * adr/0043}).
 *
 * <p><b>Sin {@code @NotNull}, y la guarda a mano no es una manía.</b> Este proyecto no tiene ningún
 * proveedor de Bean Validation en el classpath —se comprobó arrancando: {@code
 * OptionalValidatorFactoryBean} lo dice en el registro— y no hay ningún otro {@code @NotNull} en la
 * capa. La anotación habría sido un guardián que nunca dispara, que es justo lo que este proyecto
 * ya destapó con el plugin de capas.
 *
 * <p><b>Y Jackson tampoco protegía aquí</b>, contra lo que se creyó al escribir esto. La nota de
 * apps/api/CLAUDE.md —"Jackson 3 no rellena los componentes que falten de un {@code record}"— se
 * midió sobre un {@code boolean}, y un primitivo sin valor sí revienta la deserialización. Un
 * componente de tipo referencia, como este enum, <b>llega en nulo tan tranquilo</b>: se comprobó
 * mandando un cuerpo sin la clave, y la petición pasó de largo hasta morir más adelante por otra
 * razón. Sin esta comprobación, el nulo habría llegado a {@code Envio.conciliarRecaudo} y salido
 * como un 500.
 *
 * <p><b>El {@code @Schema} de abajo no valida nada: documenta.</b> Sin él, el OpenAPI que sirve el
 * backend declaraba este campo como opcional —springdoc deducía "obligatorio" de aquel
 * {@code @NotNull} que se quitó— y el cliente TypeScript generado dejaba de exigirlo en tiempo de
 * compilación, mientras el servidor seguía rechazando con 422 el cuerpo que lo omitiera. El
 * contrato publicado tiene que decir lo que el servidor de verdad exige. Lo destapó la comprobación
 * de contratos de la integración continua, que compara el OpenAPI vivo contra el cliente commiteado
 * — un guardián que sí dispara, y que aquí atrapó el efecto colateral de quitar dos que no.
 */
public record ConciliarRecaudoRequest(
    @Schema(requiredMode = RequiredMode.REQUIRED) ModalidadRecaudo modalidadRecaudo,
    long comisionRecaudo) {

  public ConciliarRecaudoRequest {
    Objects.requireNonNull(
        modalidadRecaudo,
        "modalidadRecaudo es obligatorio: sin él no se sabe por dónde entró el recaudo.");
  }
}
