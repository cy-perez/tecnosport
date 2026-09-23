# Seguridad y cumplimiento

## Autenticación

Los precios son públicos y no hay muro de registro. La autenticación existe para
el panel administrativo, el asistente de captura y la cuenta opcional del cliente.

- **Compra como invitado**, identificada por correo. Al final del checkout se
  ofrece crear cuenta con un clic reutilizando los datos ya escritos.
- **Autenticación propia** con Spring Security, no un proveedor externo. Menos
  dependencias, y la app móvil futura consume el mismo mecanismo.
- Contraseñas con Argon2id, o BCrypt con costo 12 si Argon2 complica el
  despliegue. Nunca SHA, nunca MD5, nunca sal propia.
- **Token de acceso JWT de 15 minutos** y **token de refresco de 30 días** en
  cookie `HttpOnly`, `Secure`, `SameSite=Lax`, con rotación y detección de
  reutilización: si un refresco ya usado reaparece, se invalida toda la familia de
  tokens de ese usuario.
- **El token de acceso no va en `localStorage`.** Vive en memoria.
- Roles: `CLIENTE` y `ADMIN`. Autorización por método en la capa de aplicación,
  no solo por ruta.
- Verificación de correo obligatoria al registrarse. Recuperación con token de un
  solo uso, válido 30 minutos, separado del token de verificación de correo por
  nivel de sensibilidad (`ADR-0015`). Confirmar la recuperación revoca **todas**
  las sesiones de refresco del usuario, no solo la familia de la sesión que la
  originó — perder el control de la clave es más grave que un refresco
  reutilizado.
- Límite de intentos por IP y por cuenta en inicio de sesión, registro,
  recuperación y creación de pedidos.
- El asistente de captura exige rol `ADMIN` y una sesión activa; las URL firmadas
  de subida se emiten con vencimiento corto y para un objeto específico.
- Login con Google: no en la fase 1. El diseño lo permite después sin migración.

## Pagos

- **La aprobación no se decide con lo que trae el navegador.** El parámetro de
  retorno solo pinta una pantalla; la verdad es el webhook firmado más la consulta
  directa a la transacción.
- **La firma del webhook se verifica siempre.** Evento sin firma válida se
  descarta y se registra.
- **Firma de integridad** al crear la transacción, sobre referencia, monto y
  moneda.
- Referencia única e idempotente por intento.
- Ningún dato de tarjeta toca el servidor propio. Eso mantiene el alcance de PCI
  en el mínimo y no hay que arruinarlo agregando un campo de tarjeta por
  comodidad.
- Un trabajo programado concilia pagos pendientes que nunca recibieron webhook.
- **Contraentrega:** la disponibilidad la decide el servidor, nunca el cliente.
  Cobertura (desde la cotización, `ADR-0023`), monto máximo, categorías excluidas
  e historial de rechazos son reglas de negocio en el dominio. Detalle en
  `11-pagos-y-envios.md`.
- **Envío:** el costo lo cotiza y lo elige el servidor. El cliente no manda el
  flete ni el identificador de tarifa del proveedor, por la misma razón por la
  que no manda el precio.
- **Seguimiento:** el webhook de Skydropx se verifica con su firma antes de
  aplicar nada, es idempotente por evento y responde 200 incluso al descartar.
  Un evento falsificado podría marcar un pedido como entregado, y de ahí cuelgan
  el recaudo y dos plazos legales.

## OWASP, lo que aplica aquí

- Consultas siempre parametrizadas. Ningún SQL armado por concatenación.
- Validación en el servidor de todo lo que llega, incluso de lo que el formulario
  ya validó.
- CORS con lista explícita de orígenes, no comodín.
- Cabeceras: `Content-Security-Policy` sin `unsafe-inline`, con nonce desde el
  SSR, `Strict-Transport-Security`, `X-Content-Type-Options`, `Referrer-Policy` y
  `Permissions-Policy`. La política de permisos declara explícitamente el uso de
  cámara y sensores, y solo en la ruta del asistente de captura.
- Subida de imágenes: tipo verificado por contenido y no por extensión, tamaño
  máximo, nombre regenerado, servidas desde el dominio de estáticos. El backend
  valida los objetos subidos antes de publicar un set. **Todavía no se cumple
  del todo**: la imagen principal (Fase 4, ya construida) regenera el nombre y
  sirve desde el dominio de estáticos, pero acepta el tipo por una lista blanca
  que declara el propio cliente (no verificada contra los bytes reales) y no
  tiene tamaño máximo propio — riesgo aceptado mientras solo el `ADMIN` suba
  ahí (`ADR-0016`). El set de rotación de la Fase 5 es el que debe cumplir la
  frase completa.
- Autorización verificada por recurso: que un usuario autenticado no pueda leer el
  pedido de otro cambiando el identificador.
- **Ningún redirect abierto.** `adminGuard` anota en `?destino=` a dónde iba
  quien llegó sin sesión, y el formulario de `ADMIN` vuelve ahí al entrar. Ese
  valor viene de la URL, o sea del usuario: se acepta **solo** si es una ruta
  relativa de este sitio (empieza por `/`, no por `//`) y cae dentro de
  `/admin/`; cualquier otra cosa cae al panel. Sin ese filtro, un enlace
  preparado con `?destino=https://otro-sitio` convertiría el formulario de
  ingreso en un salto a otro sitio con la credencial recién escrita, que es la
  forma más barata de robar una sesión de `ADMIN`.
- Registros sin datos personales ni tokens. Nunca se registra el cuerpo completo
  de una petición de checkout.
- Dependencias revisadas automáticamente y actualizadas.

## Marco legal colombiano

No es opcional y hay que resolverlo antes de abrir.

**Protección de datos, Ley 1581 de 2012 y Decreto 1074 de 2015**

- Política de tratamiento de datos publicada y enlazada en el pie.
- Autorización expresa en el registro y en el checkout: casilla sin marcar
  previamente, con enlace a la política. Se guarda fecha, versión del texto e IP.
- Canal para ejercer derechos: consultar, actualizar, rectificar y suprimir, con
  respuesta en los plazos de ley.
- **El registro de la base de datos ante la SIC no aplica hoy, y es la norma la
  que lo dice, no una estimación nuestra.** El Decreto 090 de 2018 dejó obligadas
  solo a las sociedades y entidades sin ánimo de lucro con activos superiores a
  100.000 UVT y a las personas jurídicas de naturaleza pública; **las personas
  naturales no están obligadas**, y este sitio lo opera una persona natural —así
  lo dice el propio aviso del titular en los tres documentos publicados—.
  Verificado el 23 de septiembre de 2026 en la SIC, que es la autoridad del
  registro. **Lo único que lo cambiaría es constituir sociedad**, y aun así solo
  por encima de ese umbral de activos. Estuvo abierto como "consultarlo con un
  contador o abogado": no lo era. **Y no estar obligado a registrar no exime de
  cumplir la Ley 1581**, que aplica igual.
- Finalidad declarada y limitada. Los datos del checkout no se usan para
  publicidad sin autorización aparte.

**Comercio electrónico, Ley 1480 de 2011**

- **El precio anunciado es el total a pagar por el producto** (art. 26), visible
  antes de pagar. Hoy sin IVA dentro, porque el negocio no es responsable de él
  (`adr/0041`).
- **Resumen del pedido antes de finalizar la transacción**, con el precio
  individual de cada producto, el precio total, **los costos de envío informados
  de forma adecuada y separada**, y la suma total a pagar. Es el **artículo 50**,
  y es la obligación que sostiene el modelo de precio base más flete de
  `adr/0021`: el envío se informa aparte porque la norma pide informarlo aparte.
- Información del proveedor antes de la compra: nombre, NIT, dirección, teléfono
  y correo.
- **Comprobante de la compra al correo registrado**, con el detalle de lo
  comprado, lo pagado y quién vendió. Es el soporte con el que quien compró
  reclama la garantía, y **no es una factura**: el negocio es una persona natural
  no responsable de IVA, así que está entre los no obligados a facturar (art.
  1.6.1.4.3 del Decreto 1625 de 2016) y optar por hacerlo lo convertiría en
  obligado de forma permanente (Resolución DIAN 000165 de 2023, art. 8 par. 1).
  Confirmado por el negocio el 18 de septiembre de 2026 como el documento de la
  venta. Ver `adr/0042`.
- **Derecho de retracto: 5 días hábiles** desde la entrega en compras a distancia.
  Debe estar explicado y ser ejercible.
- **Reversión del pago** cuando aplique, dentro de los plazos legales.
- Garantía mínima legal informada por producto. Los celulares **no** tienen una
  propia: no hay régimen especial para equipos terminales, así que es el año legal
  de cualquier producto nuevo, y el mayor que anuncie el productor si lo anuncia
  (verificado el 10 de septiembre de 2026).
- Constancia de la transacción enviada al comprador.
- Canal de PQR visible.

**Contraentrega.** El derecho de retracto y la garantía aplican igual que en el
pago en línea. El rechazo en la entrega no es un retracto: es una compra que no
se perfeccionó. Conviene distinguirlo en los términos y condiciones.

**Envío cotizado, recogida en el punto y seguimiento.** Cobrar el flete aparte
del precio agrega obligaciones que el precio-todo-incluido no tenía. Detalle
completo, con las cláusulas redactadas y la auditoría del sistema, en
`docs/12-legales-de-envio.md`. Lo esencial:

- **El costo de envío se informa antes de pagar, separado y con su valor
  exacto** (art. 50). Un "más gastos de envío" sin cifra no informa nada, y un
  flete que aparece recalculado después de aceptar el total es un cobro no
  autorizado.
- **La recogida sin costo es publicidad y obliga.** Si el sitio dice que recoger
  en el punto no paga envío, el total de ese pedido tiene que ser el subtotal
  exacto, sin un cargo con otro nombre.
- **El plazo de entrega ya no es un número inventado**: la cotización devuelve un
  plazo estimado por tarifa. Estimado del transportador y plazo prometido al
  comprador no son lo mismo, y el documento tiene que decir cuál rige.
- **El retracto y la garantía se cuentan desde la entrega**, así que la fecha de
  entrega deja de ser un dato operativo y pasa a ser el disparador de dos plazos
  legales. Por eso el seguimiento tiene conciliación programada y no solo webhook
  (`ADR-0022`).
- **Los costos de transporte de la devolución los cubre el consumidor** (art. 47),
  pero eso no alcanza al flete de ida que ya pagó ni a los costos financieros del
  reintegro. **El texto publicado ya tomó esa posición —la segura— y la dice
  expresamente** (`legales.terminos.secciones[8]`): reintegra el flete de ida y
  asume los costos financieros. Lo que queda no es una pregunta de derecho sino
  si el negocio quiere sostener otra posición; está en `docs/12` §7, punto 4,
  mudado a la lista de lo que decide el dueño.
- **Skydropx es un encargado nuevo** que recibe nombre, teléfono y dirección de
  entrega de cada comprador. Hay que declararlo en la política de datos, y
  analizar la transferencia internacional: es una plataforma mexicana, y México
  está en la lista de países con nivel adecuado de protección de la SIC — lo que
  no exime de verificar con qué entidad se contrata y dónde se procesan los datos.

- **Sistecrédito recibe el documento de identidad de quien compra**, además de
  abrirle un crédito a su nombre (`ADR-0048`). Hay que declararlo en la política
  de datos y en la casilla de autorización: el dato se transmite aunque **no se
  guarde** en este sistema —viaja del checkout a la pasarela y ahí termina—, y el
  deber de informar no depende de que lo conservemos.
  **Declarado en la política el 23 de septiembre de 2026** (versión `2026-09-23`):
  Sistecrédito entra en el numeral 5 —qué datos recolectamos—, en el 6 —datos de
  pago, que antes atribuía el crédito a Wompi— y en el 8 —encargados y
  terceros—. El texto describe el hecho sin etiquetarlo, que es lo que permitía
  cerrarlo sin esperar al abogado.
  **Y sigue abierta la pregunta que este expediente no puede responder:** si
  Sistecrédito es un *encargado* nuestro o un **segundo responsable**. Trata el
  documento para su propia finalidad —decidir y otorgar un crédito, con su propio
  contrato con esa persona—, lo que apunta a lo segundo, y de eso depende qué
  tiene que decir exactamente la **casilla de autorización**, que hoy no lo
  menciona. Es el **punto 6** de `docs/14-consultas-al-abogado.md`, con número
  propio: estuvo remitido al punto 5 y el punto 5 nunca lo mencionó, que es la
  forma de perder una pregunta sin que nadie la borre.

**Fotografías de producto.** Las imágenes son del negocio, tomadas por el negocio.
No se usan fotos de proveedores, de fabricantes ni de otras tiendas sin
autorización escrita. El asistente de captura existe, entre otras cosas, para que
no haya tentación de tomarlas de internet.

**Cookies y analítica**

- Aviso con consentimiento previo para todo lo que no sea estrictamente necesario.
  Analítica y píxeles no se cargan hasta que se acepte.
- Rechazar tiene que ser tan fácil como aceptar.

**Titular.** El sitio lo opera una persona natural. En el pie va nombre comercial
y NIT, sin sigla societaria. El nombre completo del titular sí es obligatorio en
la política de tratamiento de datos, donde la ley exige identificar al
responsable. Dato pendiente: `TODO: nombre completo del titular`.

**Advertencia.** Los borradores de política de datos, términos y condiciones y
aviso de cookies que produzca este proyecto son borradores. Los revisa un abogado
antes de publicarlos. El costo de equivocarse aquí es una sanción de la SIC.

## Estado de lo legal (Fase 6)

**Construido y verificado:**

- Política de tratamiento de datos, términos y condiciones (con retracto,
  garantías y reversión) y política de cookies, en español e inglés, publicadas
  en `/:lang/legales/*` y enlazadas en el pie. El inglés es traducción de
  cortesía y lo dice: rige el español.
- Autorización expresa en registro y checkout, con casilla propia que nunca
  arranca marcada, enlace a la política, y **constancia guardada** en
  `autorizacion_datos`: correo, versión del texto, IP, origen y fecha. La versión
  la fija el servidor (`POLITICA_DATOS_VERSION`), nunca el cliente.
- Identificación del vendedor en el pie, con el NIT corregido — el dígito de
  verificación estaba mal desde que se escribió (`1054994043-1` en vez de `-9`).

**Decidido, no olvidado:** no hay banner de cookies. El sitio no carga analítica
ni píxeles, así que solo hay cookies necesarias y de preferencia; un banner que
pide consentimiento para nada es teatro de cumplimiento. La política se
compromete a pedirlo antes de añadir analítica.

**Retracto, garantía, reversión del pago y radicación de PQR ya tienen flujo**
(2026-09-10, ver `docs/09-plan-de-arranque.md`). Ninguno mueve dinero por su
cuenta —dos de los tres métodos de pago se devuelven por fuera del sistema por
definición, y para el tercero no está verificado que la pasarela exponga la
devolución por API—, pero los cuatro dejan constancia: quién, cuándo, por qué y
con qué comprobante, que es lo que la ley pide poder demostrar.

Con ellos entraron dos caminos más que los términos publicados prometían y que
nadie había contado: **cancelación por no disponibilidad sobrevenida** y **por
incumplimiento del plazo de entrega**. Los cinco caminos que devuelven dinero
comparten una sola constancia (`Reintegro`, con su motivo), así que "cuánto
devolvimos el mes pasado" se responde sin sumar tablas a mano.

**Las decisiones que necesitan abogado viven en `docs/14-consultas-al-abogado.md`**
desde el 19 de septiembre de 2026, con la norma verificada y una recomendación
cada una. Hasta entonces eran tres preguntas sueltas dentro de una entrada del
plan de arranque, que es una forma de no entregárselas a nadie. La cuarta nació
al medir la cobertura real de envío.

**Y viven en dos sitios, no en uno.** `docs/12` §7 conserva dos puntos que son
suyos —si la reversión del pago alcanza el flete, y con qué entidad se contrata
Skydropx— porque pertenecen al análisis de envío que los abrió. Los otros tres de
esa sección se retiraron el 23 de septiembre de 2026, tachados y con su motivo.
El expediente de `docs/14` lo dice en su cabecera y remite: lo que no puede
volver a pasar es que alguien mande una de las dos listas creyendo que es la
entera, que es exactamente lo que estuvo a punto de ocurrir.

**Datos personales en reposo que no estaban en esta lista:** desde `adr/0045` la
tabla `correo_pendiente` guarda el cuerpo de cada correo transaccional hasta que
sale, y el cuerpo lleva el nombre de quien compró, su pedido y a veces su
dirección. Se purga a los treinta días — el mismo criterio que el carrito—, y la
purga corre en la misma tarea que drena la bandeja, no en una aparte que alguien
pueda apagar sin darse cuenta.

**La contradicción entre documentos publicados, corregida el 23 de septiembre de
2026.** Los términos prometían quince días hábiles para "toda petición" y la
política de datos promete diez para una consulta, y las dos frases apuntan al
mismo correo. **No necesitaba abogado y estuvo marcada como si lo necesitara**:
los arts. 14 y 15 de la Ley 1581 fijan los plazos —diez hábiles prorrogables por
cinco para la consulta, quince prorrogables por ocho para el reclamo— y la
política ya los decía bien; el que sobraba era el "toda petición" de los
términos. Verificado en el texto vigente antes de tocarlo. Ahora
`legales.terminos.secciones[11]` promete el plazo para peticiones, quejas y
reclamos y remite al numeral 12 de la política para lo que sea de datos
personales, sin repetir los números —dos copias de un plazo se desincronizan—.
**El código ya lo hacía bien y no cambia**: `TipoSolicitud` distingue los siete
tipos y `PlazosDeAtencion` los tres relojes, con los valores en
`application.yml` (`tecnosport.atencion.*`).

**Los documentos decían dos fechas de vigencia distintas, y se corrigió el 23 de
septiembre de 2026.** El encabezado que pinta la plantilla sale de
`legales.comun.version` y decía la versión vigente; el numeral "Vigencia" del
final de cada documento —los tres, en los dos idiomas— llevaba la fecha escrita a
mano y se había quedado en el 18 de septiembre, cuatro días atrás. La fecha no es
un adorno: los propios términos dicen que a cada compra se le aplica la versión
vigente el día en que se hizo, así que es el dato que decide qué texto gobierna
una reclamación.

Se arregló **quitando la copia**, no actualizándola: los tres numerales remiten
ahora al encabezado. Y como una regla que solo vive en la cabeza de quien la
escribió no es una regla, `tools/verificar-datos-de-negocio.mjs` gana una sexta
comprobación —ninguna sección de vigencia puede llevar una fecha escrita dentro—,
**verificada rompiéndola a propósito en los dos idiomas**, que es la única forma
de saber que un guardián dispara.

**Y algo que quedó desactualizado el 8 de septiembre de 2026, a propósito:** los
tres documentos publicados dicen que el precio incluye el envío y que no hay
cobros adicionales al final del proceso. Con `adr/0021` eso deja de ser cierto.
Los textos nuevos están redactados y esperan en `docs/12-legales-de-envio.md`:
**se publican en el mismo commit que encienda la cotización**, con su fecha de
versión nueva, no antes — mientras el sitio siga cobrando con el envío incluido,
publicarlos los volvería falsos en la otra dirección.
