package co.tecnosport.api.infrastructure.difusion;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El bucket en un mapa: lo que se sube se puede leer, y nada más. Lo comparten el ajustador y la
 * portada, que hacen lo mismo con él — leer la foto y dejar el resultado.
 */
class AlmacenEnMemoria implements AlmacenDeImagenes {
  static final String BASE = "https://publico.local/";
  final Map<String, byte[]> objetos = new HashMap<>();
  int subidas;

  @Override
  public void subir(String objectKey, String contentType, byte[] bytes) {
    subidas++;
    objetos.put(objectKey, bytes);
  }

  @Override
  public Optional<byte[]> leer(String objectKey) {
    return Optional.ofNullable(objetos.get(objectKey));
  }

  @Override
  public String urlPublica(String objectKey) {
    return BASE + objectKey;
  }

  @Override
  public Optional<String> objectKeyDe(String urlPublica) {
    return urlPublica.startsWith(BASE)
        ? Optional.of(urlPublica.substring(BASE.length()))
        : Optional.empty();
  }

  @Override
  public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<Long> tamanoBytes(String objectKey) {
    return Optional.ofNullable(objetos.get(objectKey)).map(b -> (long) b.length);
  }

  @Override
  public boolean eliminar(String objectKey) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int eliminarPorPrefijo(String prefijo, Set<String> conservar) {
    throw new UnsupportedOperationException();
  }
}
