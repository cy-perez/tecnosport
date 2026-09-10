package co.tecnosport.api.presentation.garantia.dto;

import java.math.BigDecimal;

/**
 * {@code monto}, {@code medio} y {@code comprobante} solo se leen con desenlace {@code REINTEGRO}.
 * Van en el mismo cuerpo y no en otra llamada porque devolver el dinero es una de las tres salidas
 * de la garantia, no un tramite aparte que alguien pueda olvidar despues de resolver.
 */
public record ResolverGarantiaRequest(
    String desenlace,
    String resumenParaElComprador,
    BigDecimal monto,
    String medio,
    String comprobante) {}
