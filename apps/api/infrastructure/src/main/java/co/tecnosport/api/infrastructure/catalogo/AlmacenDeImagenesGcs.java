package co.tecnosport.api.infrastructure.catalogo;

import co.tecnosport.api.application.catalogo.AlmacenDeImagenes;
import co.tecnosport.api.application.catalogo.UrlFirmada;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador real de {@link AlmacenDeImagenes}: Cloud Storage con URL firmada V4
 * (docs/07-infra-gcp.md). Las credenciales las resuelve el propio SDK vía {@code
 * GOOGLE_APPLICATION_CREDENTIALS} — nunca literales aquí. Sin {@code @Component}: necesita valores
 * de configuración primitivos (bucket, url pública, minutos) que solo bootstrap sabe resolver —
 * mismo criterio que {@code WompiClient}.
 */
public class AlmacenDeImagenesGcs implements AlmacenDeImagenes {

  private final Storage storage;
  private final String bucket;
  private final String urlPublicaBase;
  private final long minutosUrlFirmada;

  public AlmacenDeImagenesGcs(
      Storage storage, String bucket, String urlPublicaBase, long minutosUrlFirmada) {
    this.storage = Objects.requireNonNull(storage);
    this.bucket = Objects.requireNonNull(bucket);
    this.urlPublicaBase = Objects.requireNonNull(urlPublicaBase);
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
  public String urlPublica(String objectKey) {
    return urlPublicaBase + "/" + objectKey;
  }

  @Override
  public int eliminarPorPrefijo(String prefijo, Set<String> conservar) {
    int borrados = 0;
    for (Blob blob : storage.list(bucket, Storage.BlobListOption.prefix(prefijo)).iterateAll()) {
      if (conservar.contains(blob.getName())) {
        continue;
      }
      // Por nombre y NO con `blob.delete()`. Un Blob que viene de `list` trae su generación, y
      // borrar una generación concreta borra esa versión de verdad, saltándose el versionado del
      // bucket: no queda versión no vigente que restaurar. Sin generación, GCS mueve la versión
      // vigente a no vigente, que es la red en la que se apoya EliminarSetRotacion. Comprobado
      // contra el bucket real: con `blob.delete()` no quedaba nada que recuperar.
      storage.delete(BlobId.of(bucket, blob.getName()));
      borrados++;
    }
    return borrados;
  }
}
