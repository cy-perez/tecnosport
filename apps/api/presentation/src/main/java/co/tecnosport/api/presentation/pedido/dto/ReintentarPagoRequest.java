package co.tecnosport.api.presentation.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * El correo del pedido, que aquí hace de credencial.
 *
 * <p>Es el mismo mecanismo que ya protege {@code GET /pedidos/{id}/seguimiento}, y la asimetría
 * entre los dos endpoints del mismo recurso era el defecto: el de lectura pedía el correo y trataba
 * el que no coincide como un 404, para no confirmar siquiera que ese pedido existe; el de reintento
 * no pedía nada. Con solo el id —un UUID v7, difícil de adivinar pero que viaja en la URL de
 * retorno de la pasarela y en el correo de confirmación— se podía re-reservar inventario y leer el
 * pedido entero: correo, teléfono, dirección e historial.
 *
 * <p>En el cuerpo y no en la URL: un correo en un parámetro de consulta acaba escrito en los
 * registros de acceso de Cloud Run. El hermano de lectura lo lleva en la URL porque un {@code GET}
 * no tiene cuerpo; este sí.
 *
 * <p>El {@code @Schema} no valida nada —aquí no hay Bean Validation—: quien protege es el
 * constructor compacto. Lo que hace es que el contrato publicado, y con él el cliente TypeScript
 * generado, exijan el campo igual que lo exige el servidor.
 */
public record ReintentarPagoRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String correo) {

  public ReintentarPagoRequest {
    if (correo == null || correo.isBlank()) {
      throw new IllegalArgumentException("correo es obligatorio.");
    }
  }
}
