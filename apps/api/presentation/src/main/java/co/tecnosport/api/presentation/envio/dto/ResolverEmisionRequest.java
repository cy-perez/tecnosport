package co.tecnosport.api.presentation.envio.dto;

import java.util.List;

/**
 * Lo que la persona vio en el panel de la plataforma.
 *
 * <p>{@code veredicto} es {@code SIN_COBRO} o {@code CON_ENVIO}; el segundo exige {@code
 * enviosEnPlataforma}. Quien lo afirma no viaja en el cuerpo: sale del token, como en el resto del
 * panel.
 */
public record ResolverEmisionRequest(
    String veredicto, List<String> enviosEnPlataforma, String nota) {}
