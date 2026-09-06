package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.domain.catalogo.SetRotacion;
import co.tecnosport.api.presentation.catalogo.dto.ImagenRotacionRespuesta;
import co.tecnosport.api.presentation.catalogo.dto.SetRotacionRespuesta;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Domain -&gt; DTO del panel. A diferencia de {@code MapeadorRespuestasCatalogo}, que solo expone
 * la rotación cuando está PUBLICADA, aquí el administrador ve el set en cualquier estado: es
 * justamente lo que necesita para saber en qué va la captura.
 */
@Component
public class MapeadorRespuestasSetRotacion {

  public SetRotacionRespuesta aRespuesta(SetRotacion set) {
    List<ImagenRotacionRespuesta> imagenes =
        set.fotogramas().stream()
            .map(
                f ->
                    new ImagenRotacionRespuesta(
                        f.orden(), f.url(), f.urlWebp(), f.ancho(), f.alto()))
            .toList();
    return new SetRotacionRespuesta(
        set.id(),
        set.productoId(),
        set.fotogramasPrometidos(),
        set.estado().name(),
        set.capturadoPor(),
        set.capturadoEn(),
        set.dispositivo(),
        set.versionAsistente(),
        imagenes);
  }
}
