package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lo que el comprador tiene en la mano: el número legible de su pedido ({@code TS-2026-000123}) y
 * el correo con el que lo hizo.
 *
 * <p><b>En el cuerpo y no en la URL</b>, y por eso el endpoint es un {@code POST} aunque no cree
 * nada. Es el mismo motivo que ya llevó el correo al cuerpo en {@link ReintentarPagoRequest}: un
 * correo en un parámetro de consulta acaba escrito en los registros de acceso de Cloud Run, en el
 * historial del navegador y en la cabecera {@code Referer} de cualquier recurso que la página
 * cargue después. El hermano de lectura, {@code GET /pedidos/&#123;id&#125;/seguimiento}, lo lleva
 * en la URL porque a él se llega desde el enlace de un correo y un {@code GET} no tiene cuerpo; a
 * este se llega desde un formulario.
 *
 * <p>El {@code @Schema} no valida nada —aquí no hay Bean Validation—: quien protege es el
 * constructor compacto. Lo que hace es que el contrato publicado, y con él el cliente TypeScript
 * generado, exijan los campos igual que los exige el servidor.
 *
 * <p>Lo que <b>no</b> se valida aquí es el formato del número: eso lo decide el caso de uso, y a
 * propósito responde igual que si el pedido no existiera. Un 422 "el número no tiene el formato
 * TS-AAAA-NNNNNN" sería un oráculo gratis para quien prueba a ciegas.
 */
public record SeguimientoPorNumeroRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "TS-2026-000123")
        String numeroPedido,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String correo) {

  public SeguimientoPorNumeroRequest {
    if (numeroPedido == null || numeroPedido.isBlank()) {
      throw new IllegalArgumentException("numeroPedido es obligatorio.");
    }
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
  }
}
