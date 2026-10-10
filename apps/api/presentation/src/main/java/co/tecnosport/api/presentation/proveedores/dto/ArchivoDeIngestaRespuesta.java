package co.tecnosport.api.presentation.proveedores.dto;

import co.tecnosport.api.application.proveedores.ArchivoDeIngestaEnLista;
import co.tecnosport.api.domain.proveedores.ArchivoDeIngesta;
import java.time.Instant;
import java.util.UUID;

/**
 * Un zip del historial. Sin la key del bucket: el panel no la necesita para nada, y es la ruta de
 * un objeto privado.
 *
 * @param nombreOriginal nulo en los subidos antes de que se guardara
 * @param tamanoBytes nulo por lo mismo
 * @param borradoEn nulo mientras sigue en el bucket
 * @param lotes cuántos lotes lo leen
 * @param enUso si alguno sigue abierto: entonces no se puede borrar
 */
public record ArchivoDeIngestaRespuesta(
    UUID id,
    UUID proveedorId,
    String nombreOriginal,
    Long tamanoBytes,
    Instant subidoEn,
    Instant borradoEn,
    int lotes,
    boolean enUso) {

  public static ArchivoDeIngestaRespuesta de(ArchivoDeIngestaEnLista enLista) {
    ArchivoDeIngesta a = enLista.archivo();
    return new ArchivoDeIngestaRespuesta(
        a.id(),
        a.proveedorId(),
        a.nombreOriginal().orElse(null),
        a.tamanoBytes().orElse(null),
        a.subidoEn(),
        a.borradoEn().orElse(null),
        enLista.lotes(),
        enLista.enUso());
  }
}
