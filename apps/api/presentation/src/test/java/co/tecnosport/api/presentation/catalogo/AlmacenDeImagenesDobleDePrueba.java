package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class AlmacenDeImagenesDobleDePrueba implements AlmacenDeImagenes {

  private final Map<String, Long> objetosExistentes = new HashMap<>();

  void conObjeto(String objectKey, long bytes) {
    objetosExistentes.put(objectKey, bytes);
  }

  void limpiar() {
    objetosExistentes.clear();
  }

  @Override
  public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
    return new UrlFirmada("https://storage.googleapis.com/tecnosport-dev-imagenes/" + objectKey);
  }

  @Override
  public Optional<Long> tamanoBytes(String objectKey) {
    return Optional.ofNullable(objetosExistentes.get(objectKey));
  }

  @Override
  public String urlPublica(String objectKey) {
    return "https://storage.googleapis.com/tecnosport-dev-imagenes/" + objectKey;
  }
}
