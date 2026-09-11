package co.tecnosport.api.presentation.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Los cuatro campos del paquete van como {@code int} y no como envoltorio a propósito: Jackson 3 no
 * rellena los componentes que falten de un record, así que un cuerpo sin peso o sin una dimensión
 * muere en 422 antes de llegar al dominio. Es el resultado que se quiere — ver apps/api/CLAUDE.md.
 */
public record AgregarVariantePeticion(
    UUID productoId,
    String sku,
    long precio,
    BigDecimal tasaIva,
    String codigoBarras,
    int existenciaInicial,
    int pesoGramos,
    int largoCm,
    int anchoCm,
    int altoCm,
    List<ValorAtributoPeticion> atributos) {}
