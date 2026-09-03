package co.tecnosport.api.application.pago;

import java.util.List;

/**
 * {@code valoresPropiedadesFirma} son los valores (no los nombres) de las propiedades que Wompi
 * declaró en {@code signature.properties}, en el mismo orden — presentation ya resolvió las rutas
 * contra el JSON crudo, este caso de uso no sabe nada de esa forma (docs/11-pagos-y-envios.md).
 */
public record ProcesarEventoDePagoComando(
    String referencia,
    String estadoWompi,
    List<String> valoresPropiedadesFirma,
    long timestampFirma,
    String checksum) {}
