package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.domain.proveedores.ResumenIngesta;

public record ResumenIngestaRespuesta(
    int mensajesLeidos,
    int mensajesIgnorados,
    int mensajesNuevos,
    int publicaciones,
    int borradoresNuevos,
    int renovaciones,
    int agotados,
    int descartes,
    int alertas) {

  public static ResumenIngestaRespuesta de(ResumenIngesta r) {
    return new ResumenIngestaRespuesta(
        r.mensajesLeidos(),
        r.mensajesIgnorados(),
        r.mensajesNuevos(),
        r.publicaciones(),
        r.borradoresNuevos(),
        r.renovaciones(),
        r.agotados(),
        r.descartes(),
        r.alertas());
  }
}
