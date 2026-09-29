package co.tecnosport.api.domain.difusion;

/**
 * En qué punto está una difusión.
 *
 * <p><b>Los tres estados existen porque publicar en Instagram no es un viaje, son dos.</b> Primero
 * se crea un contenedor con la imagen y el pie, y después se publica ese contenedor. Entre los dos
 * pasos puede fallar la descarga de la imagen, caducar el contenedor o cortarse la red, y cuando
 * eso pasa no hay forma de saber desde fuera si el segundo paso llegó a ocurrir. {@code PENDIENTE}
 * es justo ese hueco: se pidió, todavía no consta que saliera.
 *
 * <p>Es el mismo problema que la emisión de guías ya resolvió —{@code ResolverEmisionIndeterminada}
 * existe por esto mismo— y la forma se copia de allí a propósito: un estado que dice «no sé» es
 * mejor que uno que miente en cualquiera de las dos direcciones. Dar por publicado lo que no salió
 * deja al panel diciendo que ya se difundió algo que nadie vio; darlo por fallido lo que sí salió
 * invita a publicarlo otra vez, y un post duplicado en Instagram no se deshace.
 */
public enum EstadoPublicacion {
  /** Se pidió la publicación y todavía no consta el resultado. */
  PENDIENTE,

  /** Salió, y existe un identificador de la publicación en la red. */
  PUBLICADA,

  /** No salió, y el motivo queda escrito para que alguien pueda decidir si reintenta. */
  FALLIDA
}
