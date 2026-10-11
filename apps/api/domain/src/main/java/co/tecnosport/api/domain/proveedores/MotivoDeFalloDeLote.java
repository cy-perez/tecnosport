package co.tecnosport.api.domain.proveedores;

/**
 * Por qué falló un lote de ingesta. Es un código y no una frase: el panel lo traduce, en español y
 * en inglés (regla dura 4). Hasta el 11 de octubre de 2026 el lote guardaba la frase en español y
 * el panel la pintaba tal cual, también en inglés.
 *
 * <p>Algunos llevan un dato que el texto interpola —la fecha que no se pudo leer, el motivo del
 * último intento—; ver {@link FalloDeLote}. El nombre de cada valor es parte del contrato con el
 * panel, que tiene una clave de traducción por cada uno: renombrarlo es cambiar las dos cosas.
 */
public enum MotivoDeFalloDeLote {
  /** El archivo no es un zip que se pueda abrir. */
  ZIP_INVALIDO,
  /** El zip no trae ningún {@code .txt} con el chat. */
  ZIP_SIN_CHAT,
  /** Descomprimido pasa del tope: no parece una exportación de chat. */
  ZIP_DEMASIADO_GRANDE,
  /** El texto no tiene ni un mensaje con fecha, hora y remitente. */
  NO_ES_UN_CHAT,
  /** Una fecha no se pudo leer; el dato es la fecha tal como venía. */
  FECHA_ILEGIBLE,
  /** Un zip de dos chats sin los dos {@code .txt} acordados. */
  ESTRUCTURA_NO_ACORDADA,
  /** La exportación ya no está en el almacén. */
  EXPORTACION_NO_ENCONTRADA,
  /** El lote no tiene archivo que leer. */
  SIN_ARCHIVO,
  /** El proveedor del lote ya no existe. */
  PROVEEDOR_NO_ENCONTRADO,
  /** La cola en memoria no admitía uno más. */
  COLA_LLENA,
  /** Cloud Tasks no aceptó la tarea. */
  COLA_NO_DISPONIBLE,
  /** Se interrumpió más veces de las que se retoma; el dato es el código del último fallo. */
  SIN_REINTENTOS,
  /** La aplicación se reinició con el lote a medias (lotes de antes del 11 de octubre de 2026). */
  REINICIO,
  /**
   * La API del modelo rechazó la cuenta —sin saldo, clave inválida, sin permiso, modelo retirado—;
   * el dato es el código HTTP. No cierra el lote: lo pausa, y se reanuda cuando la cuenta se
   * arregle.
   */
  EXTRACTOR_NO_DISPONIBLE,
  /** Un fallo del programa; el dato es el tipo de la excepción. */
  ERROR_INESPERADO,
  /** Un lote de antes de los códigos cuyo motivo no se pudo reconocer al migrar. */
  DESCONOCIDO
}
