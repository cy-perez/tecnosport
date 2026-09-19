# ADR-0045 — La bandeja de salida de correos

Fecha: 2026-09-19
Estado: aceptado
Relacionados: `adr/0044` (un correo que no sale deja de ser un silencio),
`adr/0033` (`EnTransaccionPropia`), `adr/0042` (el comprobante es el documento de la venta),
`docs/08-seguridad-legal.md`

## Contexto

`adr/0044` puso al adaptador de correo a lanzar en vez de tragarse los fallos de SMTP, y devolvió a
cada caso de uso la decisión de qué hacer con esa excepción. Fue correcto y no fue suficiente. El
propio ADR lo dejó anotado en su último apartado:

> El correo sale dentro de la transacción y antes del commit. Un fallo al comprometer deja a quien
> compró leyendo "reintegramos el dinero de tu pedido" y al sistema sin constancia de ese
> reintegro. Ningún `catch` arregla ese sentido: lo cierra una bandeja de salida.

**Ningún `catch` lo arregla porque el problema no es el fallo del correo: es el orden.** Mandar
primero y comprometer después significa que existe una ventana en la que el mensaje ya salió y el
hecho que anuncia todavía puede no ocurrir. Atrapar mejor la excepción del envío no toca esa
ventana.

Y de segundo, una deuda más pequeña que venía del mismo sitio: el `catch` de `RegistrarUsuario`
llevaba escrito que "hoy no hay ningún reenviar verificación, así que una cuenta creada el día que
el SMTP falló se queda sin verificar hasta que su dueño lo pida por otro canal".

## Decisión

**Un caso de uso no manda un correo: escribe una fila.** `EnviadorDeCorreo` conserva su firma y sus
trece llamadores, pero su adaptador de producción pasa a ser `EnviadorDeCorreoBandejaDeSalida`, que
inserta en `correo_pendiente` **uniéndose a la transacción de quien llama**. Una tarea,
`TareaBandejaDeSalida`, drena la cola con reintentos.

El envío real se va a un puerto nuevo, `TransporteDeCorreo`, con la misma firma y un solo llamador:
`DrenarBandejaDeSalida`. Que sean dos tipos y no un parámetro es deliberado — para saltarse la
bandeja hay que pedir el otro puerto por su nombre, y eso se ve en una revisión.

### Lo que esto compra, y dónde exactamente

- **Los caminos que entran por un controlador** —el reintegro, la cancelación, el despacho, el
  retracto, la radicación y la prórroga de una PQR— abren su transacción con `TransactionTemplate`.
  Ahí la fila del correo y la escritura de negocio se comprometen juntas o no se compromete
  ninguna. **Ese era el agujero, y ahí se cierra.** Si la transacción revierte, el correo no
  existió.
- **Los caminos que entran por una tarea** —el comprobante, los tres vigilantes— corren sin
  transacción a propósito, así que la fila se compromete sola. No ganan atomicidad con el reclamo
  —eso lo siguen cubriendo su `reclamar`/`liberar`— pero ganan lo que la bandeja da a todos:
  reintentos y constancia.

### Sin `REQUIRES_NEW`, y es lo contrario de `adr/0033`

`EnTransaccionPropia` existe para que una escritura **sobreviva** a lo que venga después, porque en
medio hay un tercero que cobra y ninguna transacción de base de datos revierte un cobro de Skydropx.
Aquí se quiere justo lo opuesto: que la fila **no** sobreviva a una reversión. Ponerle transacción
propia a este adaptador rompería el mecanismo entero y lo parecería arreglar, así que está escrito
en el javadoc del adaptador y no solo aquí.

### Cinco intentos, espaciados 1, 5, 15 y 40 minutos

El primero es inmediato; el último cae a poco más de una hora del primero. Una caída corta de SMTP
cabe entera ahí dentro, y una dirección que el servidor rechaza siempre deja de intentarse en vez de
dar vueltas de por vida.

**Es una constante del mecanismo, no configuración**, con el mismo criterio que las 24 horas de
`RepositorioIdempotenciaJpa`: no es una tarifa ni un plazo que el negocio pueda cambiar sin cambiar
el mecanismo. `MAX_INTENTOS` se deriva de la tabla de esperas para que no puedan contradecirse.

### El reclamo y el reintento son la misma sentencia

`reclamar` sube `intentos` y empuja `proximo_intento_en` en un solo `UPDATE` condicional. De ahí
salen tres propiedades a la vez: dos instancias no mandan el mismo correo, un proceso que muera con
el correo en la mano ya dejó su reintento programado, y **no hace falta un corte por tiempo que
suelte los reclamos atascados**. Esa última es la que importa: un `reclamado_en` aparte habría
necesitado su propio vencimiento, que es exactamente el defecto que `EmisionDeGuia` pagó con una
fila `EN_CURSO` que no vencía nunca.

### Treinta días de retención

El cuerpo de un correo lleva el nombre de quien compró, su pedido y a veces su dirección: es dato
personal en reposo (`docs/08-seguridad-legal.md`). Las filas ya enviadas se purgan a los treinta
días, el mismo criterio que la purga de carritos. Es el único parámetro configurable del mecanismo,
y lo es porque es una decisión del negocio y no una constante técnica.

### El aviso no puede ser un correo

La bandeja atascada es exactamente el estado en el que mandar un aviso es imposible. Los otros
vigilantes del sistema avisan por correo porque su fallo no afecta al correo; este sí. **La única
señal que queda es el registro en `error`** con los rendidos de cada vuelta. Convertir esa línea en
una alerta es infraestructura —una política de Cloud Logging— y pertenece a la etapa de producción.

Se anota aquí porque es la parte floja del diseño y no conviene que se descubra sola.

### Los trece `catch` se quedan, y sus comentarios no

Encolar prácticamente no puede fallar, así que los trece `catch` pasan a atrapar un fallo de base de
datos — caso en el que la transacción de quien llama está condenada de todas formas. Se dejaron
puestos porque quitarlos obligaría a que un error de escritura se propagara distinto en trece
sitios.

Lo que no se dejó fue el comentario viejo. Trece justificaciones elaboradas que ya no describen lo
que protegen son el mismo género de podredumbre que este proyecto viene cazando: el plugin de capas
que aceptaba la configuración sin aplicarla, el doble cuyo reclamo atómico era un `Set.add()`, la
prueba en verde que fijaba el defecto. **Un comentario que dejó de ser cierto miente con más
autoridad que el código**, porque nadie lo compila.

Por lo mismo se corrigieron tres `@return` que decían "si el correo salió" y hoy dicen "si quedó
encolado", y el párrafo de `ResultadoComprobantes` que todavía afirmaba que el adaptador se tragaba
los fallos de SMTP.

### Y de paso, reenviar la verificación

Con la bandeja puesta es un caso de uso corto, y cierra la deuda del `catch` de `RegistrarUsuario`
por los dos lados: un SMTP caído ya no pierde el correo, y además existe un sitio donde pedir otro
enlace cuando el que había caducó.

Responde 204 exista la cuenta, no exista, o exista ya verificada. Los tres tienen que ser
indistinguibles o esto se convierte en un oráculo para averiguar qué correos tienen cuenta aquí y en
qué estado — el mismo razonamiento que sostiene `SolicitarRecuperacion`. El límite por cuenta va
antes de buscarla: sin eso es una forma cómoda de llenarle la casilla a cualquiera desde un
formulario público.

Su ruta se añadió **explícita** al filtro de límite por IP. Un patrón exacto no cubre subrutas, así
que `/api/v1/auth/verificacion` no protegía a `/api/v1/auth/verificacion/reenviar` — el mismo
descuido que dejó sin límite el endpoint que cotiza contra Skydropx, y que esta vez atrapó al primer
intento el guardián que enumera las rutas del filtro.

## Alternativas consideradas

**Dejarlo como estaba y confiar en que la ventana es pequeña.** Lo es: entre el envío y el commit
hay milisegundos. Pero el contenido de esos correos es un reintegro de dinero, una cancelación y el
documento de la venta, y "poco probable" no es un argumento cuando lo que está en juego es que
alguien lea que se le devolvió una plata de la que no queda constancia.

**Mandar el correo después del commit, con un `afterCommit` de Spring.** Cierra la ventana y es
mucho más barato. Se descartó porque no deja constancia ni reintenta: si el proceso muere justo
después de comprometer, el correo no se manda nunca y nadie se entera. Además obligaría a que
`application` supiera de Spring, que es la regla dura #1.

**Un servicio de cola de verdad (Pub/Sub, una cola de Cloud Tasks).** Es lo correcto a otra escala.
Aquí añade una dependencia de infraestructura, un modo de fallo nuevo y un coste, para un volumen
que cabe de sobra en una tabla y una tarea cada minuto. Si algún día el volumen lo pide, el puerto
`RepositorioCorreosPendientes` es exactamente la costura por donde se cambia.

## Consecuencias

- **El correo deja de ser instantáneo:** llega con hasta un minuto de retraso. Se nota sobre todo en
  la verificación al registrarse, y es el precio explícito de todo lo demás. El intervalo se puede
  bajar, no hay nada que lo impida.
- **Nadie puede volver a preguntar "¿salió el correo?" en el sitio de la llamada**, y eso es
  deliberado: la respuesta honesta es que todavía no se sabe. Quien quiera saberlo mira
  `correo_pendiente`.
- **Hay una tabla nueva con datos personales en reposo**, con su purga y su comentario en la
  migración diciendo por qué.
- **Queda pendiente la alerta sobre el registro de rendidos**, que es infraestructura.
