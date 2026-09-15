package co.tecnosport.api.application.pago;

import java.util.List;

/**
 * {@code valoresPropiedadesFirma} son los valores (no los nombres) de las propiedades que Wompi
 * declaró en {@code signature.properties}, en el mismo orden — presentation ya resolvió las rutas
 * contra el JSON crudo, este caso de uso no sabe nada de esa forma (docs/11-pagos-y-envios.md).
 *
 * <p>{@code medioWompi} es el {@code payment_method_type} de la transacción: con qué se cobró de
 * verdad, que no tiene por qué ser lo que el comprador eligió en nuestro checkout — el Web Checkout
 * hospedado no recibe esa elección y pinta su propia lista. Puede venir nulo o vacío si el evento
 * no lo trae, y eso no invalida nada: simplemente no se sabe todavía.
 */
public record ProcesarEventoDePagoComando(
    String referencia,
    String estadoWompi,
    String medioWompi,
    List<String> valoresPropiedadesFirma,
    long timestampFirma,
    String checksum) {}
