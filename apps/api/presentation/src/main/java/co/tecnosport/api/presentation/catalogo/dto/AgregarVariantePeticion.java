package co.tecnosport.api.presentation.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AgregarVariantePeticion(
    UUID productoId,
    String sku,
    long precio,
    BigDecimal tasaIva,
    String codigoBarras,
    int existenciaInicial,
    List<ValorAtributoPeticion> atributos) {}
