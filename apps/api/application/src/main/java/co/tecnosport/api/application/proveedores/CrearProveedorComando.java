package co.tecnosport.api.application.proveedores;

import co.tecnosport.api.domain.catalogo.LineaCatalogo;
import java.math.BigDecimal;

/**
 * @param factorDeMargen nulo para usar el de la línea
 */
public record CrearProveedorComando(
    String nombre,
    LineaCatalogo linea,
    String telefonoWhatsApp,
    String nombreEnExportacion,
    BigDecimal factorDeMargen) {}
