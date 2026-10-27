package co.tecnosport.api.infrastructure.proveedores;

import co.tecnosport.api.application.catalogo.UrlFirmada;
import co.tecnosport.api.application.proveedores.AlmacenDeArchivosDeProveedor;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * El bucket privado de los originales del proveedor, sobre el mismo {@code Storage} que el de
 * imágenes: misma cuenta de servicio, mismas credenciales por defecto de la aplicación, otro
 * bucket. Nada de aquí es público: leer una foto en el panel pasa por una URL firmada de {@code
 * GET} y de vida corta.
 */
public class AlmacenDeArchivosDeProveedorGcs implements AlmacenDeArchivosDeProveedor {

  private final Storage storage;
  private final String bucket;
  private final long minutosUrlFirmada;

  public AlmacenDeArchivosDeProveedorGcs(Storage storage, String bucket, long minutosUrlFirmada) {
    this.storage = Objects.requireNonNull(storage);
    this.bucket = Objects.requireNonNull(bucket);
    this.minutosUrlFirmada = minutosUrlFirmada;
  }

  @Override
  public UrlFirmada generarUrlDeSubida(String objectKey, String contentType) {
    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).build();
    URL url =
        storage.signUrl(
            blobInfo,
            minutosUrlFirmada,
            TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withExtHeaders(Map.of("Content-Type", contentType)),
            Storage.SignUrlOption.withV4Signature());
    return new UrlFirmada(url.toString());
  }

  @Override
  public Optional<Long> tamanoBytes(String objectKey) {
    Blob blob = storage.get(bucket, objectKey);
    return blob == null ? Optional.empty() : Optional.of(blob.getSize());
  }

  @Override
  public void guardar(String objectKey, String contentType, byte[] bytes) {
    BlobInfo blobInfo =
        BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).setContentType(contentType).build();
    storage.create(blobInfo, bytes);
  }

  @Override
  public Optional<byte[]> leer(String objectKey) {
    Blob blob = storage.get(bucket, objectKey);
    return blob == null ? Optional.empty() : Optional.of(blob.getContent());
  }

  @Override
  public UrlFirmada urlDeLectura(String objectKey) {
    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).build();
    URL url =
        storage.signUrl(
            blobInfo,
            minutosUrlFirmada,
            TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(HttpMethod.GET),
            Storage.SignUrlOption.withV4Signature());
    return new UrlFirmada(url.toString());
  }

  @Override
  public void borrar(String objectKey) {
    storage.delete(BlobId.of(bucket, objectKey));
  }

  @Override
  public int borrarPorPrefijo(String prefijo) {
    if (prefijo == null || !prefijo.endsWith("/")) {
      throw new IllegalArgumentException("El prefijo de un borrado termina en «/»: " + prefijo);
    }
    int borrados = 0;
    for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(prefijo)).iterateAll()) {
      // Por nombre, sin la generación que trae el Blob de la lista, como en AlmacenDeImagenesGcs:
      // con ella se borraría esa versión de verdad, saltándose el versionado del bucket.
      storage.delete(BlobId.of(bucket, blob.getName()));
      borrados++;
    }
    return borrados;
  }
}
