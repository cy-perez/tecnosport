package co.tecnosport.api.application.catalogo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Doble de prueba escrito a mano, sin Mockito, ver docs/06-testing.md. */
final class AlmacenDeImagenesFalso implements AlmacenDeImagenes {

  private static final String BASE_PUBLICA = "https://storage.googleapis.com/bucket-falso/";

  private final Map<String, Long> objetos = new HashMap<>();
  final List<String> prefijosEliminados = new ArrayList<>();
  final List<String> objetosEliminados = new ArrayList<>();
  boolean fallarAlEliminar;
  String ultimoObjectKeyFirmado;
  String ultimoContentTypeFirmado;

  boolean existe(String objectKey) {
    return objetos.containsKey(objectKey);
  }

  void conObjeto(String objectKey, long bytes) {
    objetos.put(objectKey, bytes);
  }

  @Override
  public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
    this.ultimoObjectKeyFirmado = objectKey;
    this.ultimoContentTypeFirmado = contentType;
    return new UrlFirmada(BASE_PUBLICA + objectKey + "?firmada");
  }

  @Override
  public Optional<Long> tamanoBytes(String objectKey) {
    return Optional.ofNullable(objetos.get(objectKey));
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
    if (fallarAlEliminar) {
      throw new IllegalStateException("El almacén falló al borrar.");
    }
    objetosEliminados.add(objectKey);
    return objetos.remove(objectKey) != null;
  }

  @Override
  public int eliminarPorPrefijo(String prefijo, Set<String> conservar) {
    if (fallarAlEliminar) {
      throw new IllegalStateException("El almacén falló al borrar.");
    }
    prefijosEliminados.add(prefijo);
    List<String> aBorrar =
        objetos.keySet().stream()
            .filter(key -> key.startsWith(prefijo) && !conservar.contains(key))
            .toList();
    aBorrar.forEach(objetos::remove);
    return aBorrar.size();
  }
}
