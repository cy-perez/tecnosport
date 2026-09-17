package co.tecnosport.api.presentation.envio.dto;

/**
 * Qué concluyó quien miró. La nota es opcional —obligarla llenaría la tabla de "ok"— y quién la
 * escribió no viaja en el cuerpo: sale del token, como en el resto del panel.
 */
public record AcusarRevisionRequest(String nota) {}
