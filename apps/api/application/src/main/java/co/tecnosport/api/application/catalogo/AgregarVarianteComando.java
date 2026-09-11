package co.tecnosport.api.application.catalogo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AgregarVarianteComando(
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
    List<ValorAtributoComando> atributos) {}
