package co.tecnosport.api.presentation.catalogo;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
class AlmacenDeImagenesDobleDePrueba implements AlmacenDeImagenes {

  private static final String BASE_PUBLICA =
      "https://storage.googleapis.com/tecnosport-dev-imagenes/";

  private final Map<String, Long> objetosExistentes = new HashMap<>();
  final List<String> objetosEliminados = new ArrayList<>();

  void conObjeto(String objectKey, long bytes) {
    objetosExistentes.put(objectKey, bytes);
  }

  void limpiar() {
    objetosExistentes.clear();
    objetosEliminados.clear();
  }

  boolean existe(String objectKey) {
    return objetosExistentes.containsKey(objectKey);
  }

  @Override
  public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
    return new UrlFirmada(BASE_PUBLICA + objectKey);
  }

  @Override
  public Optional<Long> tamanoBytes(String objectKey) {
    return Optional.ofNullable(objetosExistentes.get(objectKey));
  }

  @Override
  public String urlPublica(String objectKey) {
    return BASE_PUBLICA + objectKey;
  }

  @Override
  public Optional<String> objectKeyDe(String urlPublica) {
    return urlPublica.startsWith(BASE_PUBLICA)
        ? Optional.of(urlPublica.substring(BASE_PUBLICA.length()))
        : Optional.empty();
  }

  @Override
  public boolean eliminar(String objectKey) {
    objetosEliminados.add(objectKey);
    return objetosExistentes.remove(objectKey) != null;
  }

  @Override
  public int eliminarPorPrefijo(String prefijo, Set<String> conservar) {
    List<String> aBorrar =
        objetosExistentes.keySet().stream()
            .filter(key -> key.startsWith(prefijo) && !conservar.contains(key))
            .toList();
    aBorrar.forEach(objetosExistentes::remove);
    return aBorrar.size();
  }
}
