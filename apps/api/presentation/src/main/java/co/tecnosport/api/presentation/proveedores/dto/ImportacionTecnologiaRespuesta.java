package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.tecnologia.ImportarListaDeTecnologia;
import java.util.List;

/** Lo que hizo la lista. Ver {@link ImportarListaDeTecnologia.Resultado}. */
public record ImportacionTecnologiaRespuesta(
    int productosRenovados,
    int variantesRepuestas,
    int variantesRetiradas,
    int modelosAgotados,
    int borradoresNuevos,
    int borradoresActualizados,
    List<String> modelosYaDecididos,
    List<String> sinMargen) {

  public static ImportacionTecnologiaRespuesta de(ImportarListaDeTecnologia.Resultado r) {
    return new ImportacionTecnologiaRespuesta(
        r.productosRenovados(),
        r.variantesRepuestas(),
        r.variantesRetiradas(),
        r.modelosAgotados(),
        r.borradoresNuevos(),
        r.borradoresActualizados(),
        r.modelosYaDecididos(),
        r.sinMargen());
  }
}
