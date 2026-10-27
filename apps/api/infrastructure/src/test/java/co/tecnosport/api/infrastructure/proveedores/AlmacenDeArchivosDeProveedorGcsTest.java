package co.tecnosport.api.infrastructure.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * El borrado por prefijo contra el SDK de Cloud Storage. Con Mockito y no con un doble a mano: la
 * interfaz {@code Storage} tiene más de cien métodos, y un {@code Blob} no se construye fuera del
 * SDK.
 */
class AlmacenDeArchivosDeProveedorGcsTest {

  private static final String BUCKET = "tecnosport-proveedores";
  private static final String PREFIJO = "proveedores/p/borradores-tecnologia/b/";

  @Test
  void borraPorNombreCadaObjetoDelPrefijoYDiceCuantos() {
    Storage storage = mock(Storage.class);
    Blob uno = blob(PREFIJO + "1-1200.avif");
    Blob otro = blob(PREFIJO + "x.avif");
    @SuppressWarnings("unchecked")
    Page<Blob> pagina = mock(Page.class);
    when(pagina.iterateAll()).thenReturn(List.of(uno, otro));
    when(storage.list(eq(BUCKET), any(Storage.BlobListOption[].class))).thenReturn(pagina);

    int borrados =
        new AlmacenDeArchivosDeProveedorGcs(storage, BUCKET, 15).borrarPorPrefijo(PREFIJO);

    assertThat(borrados).isEqualTo(2);
    // Sin generación: con la del Blob de la lista se saltaría el versionado del bucket.
    verify(storage).delete(BlobId.of(BUCKET, PREFIJO + "1-1200.avif"));
    verify(storage).delete(BlobId.of(BUCKET, PREFIJO + "x.avif"));
  }

  @Test
  void unPrefijoSinBarraFinalNoSeAcepta() {
    Storage storage = mock(Storage.class);

    assertThatThrownBy(
            () ->
                new AlmacenDeArchivosDeProveedorGcs(storage, BUCKET, 15)
                    .borrarPorPrefijo("proveedores/p/borradores-tecnologia/b"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Blob blob(String nombre) {
    Blob blob = mock(Blob.class);
    when(blob.getName()).thenReturn(nombre);
    return blob;
  }
}
