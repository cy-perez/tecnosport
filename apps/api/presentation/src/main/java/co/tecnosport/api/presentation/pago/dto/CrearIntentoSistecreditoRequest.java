package co.tecnosport.api.presentation.pago.dto;

import co.tecnosport.api.domain.compartido.TipoDocumento;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * El documento de quien pide el crédito viaja en cada intento porque <b>no se guarda</b> ({@code
 * adr/0048}): Sistecrédito lo exige para encontrar al cliente, y es lo único que este endpoint
 * recibe del cliente además del pedido. El monto no: sale del pedido (regla dura #7).
 *
 * <p>Las tres validaciones están en el constructor compacto y no en anotaciones porque aquí no hay
 * Bean Validation (apps/api/CLAUDE.md). El {@code @Schema} no valida nada: solo hace que el
 * contrato publicado diga lo que el servidor de verdad exige.
 */
public record CrearIntentoSistecreditoRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID pedidoId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TipoDocumento tipoDocumento,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String documento,
    @Schema(description = "Idioma al que vuelve el comprador: es o en. Cualquier otro cae en es.")
        String idioma) {

  public CrearIntentoSistecreditoRequest {
    if (pedidoId == null) {
      throw new IllegalArgumentException("pedidoId es obligatorio.");
    }
    if (tipoDocumento == null) {
      throw new IllegalArgumentException("tipoDocumento es obligatorio.");
    }
    // Sin citar el valor recibido, a diferencia del resto de los DTO: es un dato personal y no
    // tiene por qué acabar en un registro de errores para explicar que llegó vacío.
    if (documento == null || documento.isBlank()) {
      throw new IllegalArgumentException("documento es obligatorio.");
    }
  }
}
