package co.tecnosport.api.domain.proveedores;

import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.compartido.GeneradorIdentificador;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El zip que se subió para una ingesta, visto como archivo: con qué nombre llegó, cuánto pesa, y si
 * todavía ocupa el bucket. Uno o dos lotes lo leen (dos cuando el proveedor sube sus dos chats
 * juntos); este es el registro del archivo, no del trabajo.
 *
 * <p>Existe para la limpieza periódica del bucket privado: el zip no se vuelve a leer después de
 * procesar el lote —las fotos quedaron en objetos propios y rehacer una ingesta es volver a
 * subirla—, así que guardarlo para siempre solo cuesta. Borrarlo deja el lote, sus mensajes y sus
 * borradores tal cual: lo único que se va son los bytes.
 *
 * <p>El nombre lo manda el navegador y solo sirve para reconocer el archivo en la lista. Se guarda
 * sin carpetas y recortado: nunca se usa para nada que no sea mostrarlo.
 */
public final class ArchivoDeIngesta {

  /** El de la columna; un nombre de archivo de verdad no se acerca. */
  public static final int LARGO_MAXIMO_DEL_NOMBRE = 255;

  private final UUID id;
  private final String referencia;
  private final UUID proveedorId;
  private final String nombreOriginal;
  private final Long tamanoBytes;
  private final Instant subidoEn;
  private Instant borradoEn;

  public ArchivoDeIngesta(
      UUID id,
      String referencia,
      UUID proveedorId,
      String nombreOriginal,
      Long tamanoBytes,
      Instant subidoEn,
      Instant borradoEn) {
    this.id = Objects.requireNonNull(id, "El id del archivo no puede ser nulo.");
    this.referencia = Objects.requireNonNull(referencia, "El archivo tiene una key en el bucket.");
    if (referencia.isBlank()) {
      throw new ExcepcionDeDominio("La key del archivo no puede estar vacía.");
    }
    this.proveedorId = Objects.requireNonNull(proveedorId, "Un archivo es de un proveedor.");
    this.nombreOriginal = limpiarNombre(nombreOriginal);
    if (tamanoBytes != null && tamanoBytes < 0) {
      throw new ExcepcionDeDominio("El tamaño de un archivo no es negativo.");
    }
    this.tamanoBytes = tamanoBytes;
    this.subidoEn = Objects.requireNonNull(subidoEn, "La fecha de subida no puede ser nula.");
    this.borradoEn = borradoEn;
  }

  /** El zip acaba de entrar y ya se comprobó que está en el bucket. */
  public static ArchivoDeIngesta recibir(
      String referencia, UUID proveedorId, String nombreOriginal, long tamanoBytes, Instant ahora) {
    return new ArchivoDeIngesta(
        GeneradorIdentificador.nuevo(),
        referencia,
        proveedorId,
        nombreOriginal,
        tamanoBytes,
        ahora,
        null);
  }

  /** Los bytes se fueron del bucket. Una sola vez: la fecha es la de la primera. */
  public void marcarBorrado(Instant ahora) {
    if (borradoEn == null) {
      borradoEn = Objects.requireNonNull(ahora, "La fecha de borrado no puede ser nula.");
    }
  }

  public boolean borrado() {
    return borradoEn != null;
  }

  /**
   * Sin carpetas —algunos navegadores viejos mandaban la ruta entera— y sin espacios a los lados,
   * recortado al largo de la columna. Vacío es nulo: el panel muestra un guion.
   */
  private static String limpiarNombre(String nombre) {
    if (nombre == null) {
      return null;
    }
    String base = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
    String limpio = base.strip();
    if (limpio.isEmpty()) {
      return null;
    }
    return limpio.length() > LARGO_MAXIMO_DEL_NOMBRE
        ? limpio.substring(0, LARGO_MAXIMO_DEL_NOMBRE)
        : limpio;
  }

  public UUID id() {
    return id;
  }

  public String referencia() {
    return referencia;
  }

  public UUID proveedorId() {
    return proveedorId;
  }

  public Optional<String> nombreOriginal() {
    return Optional.ofNullable(nombreOriginal);
  }

  /** Nulo en los zips subidos antes de que se guardara: el tamaño se leía y se descartaba. */
  public Optional<Long> tamanoBytes() {
    return Optional.ofNullable(tamanoBytes);
  }

  public Instant subidoEn() {
    return subidoEn;
  }

  public Optional<Instant> borradoEn() {
    return Optional.ofNullable(borradoEn);
  }
}
