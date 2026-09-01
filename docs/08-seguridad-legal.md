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
  solo uso, válido 30 minutos.
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
  Cobertura, monto máximo, categorías excluidas e historial de rechazos son reglas
  de negocio en el dominio. Detalle en `11-pagos-y-envios.md`.

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
  valida los objetos subidos antes de publicar un set.
- Autorización verificada por recurso: que un usuario autenticado no pueda leer el
  pedido de otro cambiando el identificador.
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
- Evaluar el registro de la base de datos ante la SIC. Aplica según los activos
  del responsable: **consultarlo con un contador o abogado**, no asumirlo.
- Finalidad declarada y limitada. Los datos del checkout no se usan para
  publicidad sin autorización aparte.

**Comercio electrónico, Ley 1480 de 2011**

- **Precios con IVA incluido**, visibles antes de pagar.
- Información del proveedor antes de la compra: nombre, NIT, dirección, teléfono
  y correo.
- **Derecho de retracto: 5 días hábiles** desde la entrega en compras a distancia.
  Debe estar explicado y ser ejercible.
- **Reversión del pago** cuando aplique, dentro de los plazos legales.
- Garantía mínima legal informada por producto, y la propia de los celulares.
- Constancia de la transacción enviada al comprador.
- Canal de PQR visible.

**Contraentrega.** El derecho de retracto y la garantía aplican igual que en el
pago en línea. El rechazo en la entrega no es un retracto: es una compra que no
se perfeccionó. Conviene distinguirlo en los términos y condiciones.

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
