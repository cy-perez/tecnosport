package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.inventario.ExistenciaDeVariante;
import co.tecnosport.api.application.inventario.ExistenciasDelCatalogo;
import co.tecnosport.api.application.inventario.ResultadoDeAjuste;
import co.tecnosport.api.presentation.catalogo.dto.ExistenciaAjustadaRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ExistenciaDeVarianteRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.ExistenciasRespuesta;
import org.springframework.stereotype.Component;

/**
 * Las existencias del catálogo y el resultado de ajustarlas. Mapeador propio, por el mismo motivo
 * que {@link MapeadorVariantesSinMedir}: esto no es el catálogo visto desde el panel, es una
 * pantalla de conteo con sus propias cifras.
 */
@Component
public class MapeadorExistencias {

  public ExistenciasRespuesta aRespuesta(ExistenciasDelCatalogo existencias) {
    return new ExistenciasRespuesta(
        existencias.total(),
        existencias.totalDescuadradas(),
        existencias.totalDescuadradasEnPublicados(),
        existencias.variantes().stream().map(MapeadorExistencias::aRespuesta).toList());
  }

  public ExistenciaAjustadaRespuesta aRespuesta(ResultadoDeAjuste resultado) {
    return new ExistenciaAjustadaRespuesta(
        resultado.varianteId(),
        resultado.sku(),
        resultado.nombreProducto(),
        resultado.saldoAnterior(),
        resultado.saldoNuevo(),
        resultado.diferencia(),
        resultado.unidadesReservadas(),
        resultado.sinCambios(),
        resultado.dejaReservasSinRespaldo());
  }

  private static ExistenciaDeVarianteRespuesta aRespuesta(ExistenciaDeVariante existencia) {
    return new ExistenciaDeVarianteRespuesta(
        existencia.varianteId(),
        existencia.productoId(),
        existencia.nombreProducto(),
        existencia.sku(),
        existencia.estadoProducto().name(),
        existencia.existenciaDeclarada(),
        existencia.saldoTotal(),
        existencia.disponible(),
        existencia.reservadas(),
        existencia.descuadrada());
  }
}
