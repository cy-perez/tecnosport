package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.InventarioSinMedir;
import co.tecnosport.api.application.catalogo.MedidaDeVariante;
import co.tecnosport.api.application.catalogo.MedidasDelCatalogo;
import co.tecnosport.api.application.catalogo.ResultadoDeMedicion;
import co.tecnosport.api.application.catalogo.VarianteSinMedir;
import co.tecnosport.api.domain.catalogo.Paquete;
import co.tecnosport.api.presentation.catalogo.dto.MedidaDeVarianteRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.MedidasRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VarianteMedidaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VarianteSinMedirRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.VariantesSinMedirRespuesta;
import org.springframework.stereotype.Component;

/**
 * Lo que falta por medir, y el resultado de medirlo. Mapeador propio y no un método más de {@link
 * MapeadorRespuestasProductoAdmin} porque esto no es el catálogo visto desde el panel: es una lista
 * de trabajo pendiente, con sus propios campos y sus propios conteos.
 */
@Component
public class MapeadorVariantesSinMedir {

  public VariantesSinMedirRespuesta aRespuesta(InventarioSinMedir inventario) {
    return new VariantesSinMedirRespuesta(
        inventario.total(),
        inventario.totalEnPublicados(),
        inventario.variantes().stream().map(MapeadorVariantesSinMedir::aRespuesta).toList());
  }

  public MedidasRespuesta aRespuesta(MedidasDelCatalogo medidas) {
    return new MedidasRespuesta(
        medidas.total(),
        medidas.totalSinMedir(),
        medidas.totalSinMedirEnPublicados(),
        medidas.variantes().stream().map(MapeadorVariantesSinMedir::aRespuesta).toList());
  }

  public VarianteMedidaRespuesta aRespuesta(ResultadoDeMedicion resultado) {
    // El paquete está presente por construcción: MedirVariante acaba de ponerlo. El orElseThrow es
    // el que corresponde a un Optional que no puede estar vacío, no una rama de negocio.
    Paquete paquete = resultado.variante().paquete().orElseThrow();
    return new VarianteMedidaRespuesta(
        resultado.variante().id(),
        resultado.variante().sku().valor(),
        paquete.pesoGramos(),
        paquete.largoCm(),
        paquete.anchoCm(),
        paquete.altoCm(),
        resultado.correccion());
  }

  private static MedidaDeVarianteRespuesta aRespuesta(MedidaDeVariante medida) {
    Paquete paquete = medida.paquete();
    return new MedidaDeVarianteRespuesta(
        medida.varianteId(),
        medida.productoId(),
        medida.nombreProducto(),
        medida.sku(),
        medida.estadoProducto().name(),
        paquete == null ? null : paquete.pesoGramos(),
        paquete == null ? null : paquete.largoCm(),
        paquete == null ? null : paquete.anchoCm(),
        paquete == null ? null : paquete.altoCm(),
        medida.sinMedir());
  }

  private static VarianteSinMedirRespuesta aRespuesta(VarianteSinMedir variante) {
    return new VarianteSinMedirRespuesta(
        variante.varianteId(),
        variante.productoId(),
        variante.nombreProducto(),
        variante.sku(),
        variante.estadoProducto().name());
  }
}
