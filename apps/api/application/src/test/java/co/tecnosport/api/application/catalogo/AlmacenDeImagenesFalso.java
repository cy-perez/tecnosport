package co.tecnosport.api.application.catalogo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class AlmacenDeImagenesFalso implements AlmacenDeImagenes {

  private final Map<String, Long> objetos = new HashMap<>();
  String ultimoObjectKeyFirmado;
  String ultimoContentTypeFirmado;

  void conObjeto(String objectKey, long bytes) {
    objetos.put(objectKey, bytes);
  }

  @Override
  public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
    this.ultimoObjectKeyFirmado = objectKey;
    this.ultimoContentTypeFirmado = contentType;
    return new UrlFirmada("https://storage.googleapis.com/bucket-falso/" + objectKey + "?firmada");
  }

  @Override
  public Optional<Long> tamanoBytes(String objectKey) {
    return Optional.ofNullable(objetos.get(objectKey));
  }

  @Override
  public String urlPublica(String objectKey) {
    return "https://storage.googleapis.com/bucket-falso/" + objectKey;
  }
}
