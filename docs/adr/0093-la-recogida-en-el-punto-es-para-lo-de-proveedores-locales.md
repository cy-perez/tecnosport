# ADR-0093: la recogida en el punto es para lo de proveedores locales

Fecha: 2026-10-26
Estado: aceptada. La regla está construida; **la recogida sigue apagada** hasta cerrar lo que
dice «Lo que falta para encenderla».
Matiza: `ADR-0072` (la recogida se apaga con una bandera del servidor).

## Contexto

Desde el 8 de octubre de 2026 la recogida en el punto de Medellín no se ofrece (`ADR-0072`).
El negocio decidió el 26 de octubre volver a ofrecerla, pero no para todo: solo para lo que surten
los proveedores de la ciudad, La Riverah y Violeta, porque es lo único que llega al punto a tiempo.
La tecnología del mayorista, los productos cargados a mano y lo de los demás proveedores siguen
yendo a domicilio.

El punto es el mismo de los despachos (`ORIGEN_DIRECCION`): Cra. 26C # 38B-31, apto. 401, La
Milagrosa, Medellín. Es un apartamento: la entrega se acuerda con el comprador, cualquier día entre
las 8:00 a. m. y las 9:00 p. m.

## Decisión

1. **Ser local es un dato del proveedor, no un nombre en el código.** `Proveedor.esLocal()`, columna
   `proveedor.es_local` (V122), que se marca desde el panel y vale para cualquier línea. La V122
   marca a La Riverah y a Violeta por nombre, como hizo la V113 con los sets, porque el id no es
   el mismo en desarrollo que en la nube.
2. **La recogida se ofrece solo si todo el carrito es de proveedores locales y activos**
   (`RecogidaEnPunto`). Un solo artículo de otro origen la quita para el carrito entero, porque el
   sistema no tiene pedidos parciales. Un producto manual no tiene proveedor y por eso no es local.
   Lo que no está a la venta (borrador, oculto, agotado) no se recoge.
3. **La bandera sigue siendo el interruptor general.** `RETIRO_EN_PUNTO_HABILITADO` apagada quita la
   recogida aunque todo el carrito sea local; encendida, decide el carrito.
4. **La exige el servidor con los productos reales.** `CrearPedido` y `ReintentarPago` responden 409
   `RETIRO_EN_PUNTO_NO_DISPONIBLE` antes de reservar, y después del rechazo barato de las líneas
   repetidas. Al exigirla, lo que no se vende no decide por sí solo: si todo lo demás es local, el
   caso de uso responde su 404 como antes; si hay además algo que no es local, responde el 409.
5. **El checkout pregunta por carrito.** `GET /envios/modalidades` pasa a ser
   `POST /envios/modalidades` con `{varianteIds}`: la respuesta ya no es la misma para todos. El
   `GET` se retiró en vez de dejarlo diciendo que hay recogida para cualquier carrito; un cliente
   viejo recibe 405 y lo trata como recogida apagada, que es el lado seguro. La ruta va bajo el
   límite por IP de la cotización, y `RecogidaEnPunto` quita repetidos y no lee más de 50 variantes
   distintas: cada una es una lectura del catálogo y la consulta es pública.

## Lo que falta para encenderla

Encender la bandera hoy haría una promesa que el sistema no cumple. Faltan tres cosas, y van juntas
en un PR aparte:

- **Entregar en el punto.** Un pedido de recogida solo llega a `ENTREGADO` pasando por
  `DESPACHADO`, que exige una guía de transportadora (`Envio` no admite cero guías) y le manda al
  comprador el correo de «despachado con la transportadora». Hace falta un camino propio: avisar
  que el pedido está listo para recoger y marcarlo recogido. Del momento de la entrega corren el
  retracto y la garantía, así que tiene que quedar registrado.
- **Los textos legales.** Los términos (§8), las preguntas frecuentes y la página de contacto dicen
  hoy que no hay recogida, y eso sigue siendo cierto mientras la bandera esté apagada. Se cambian
  con la skill `textos-legales-comerciales` el mismo día que se enciende la bandera, no antes.
- **El pago en el punto.** El negocio quiere que quien recoge pueda pagar al recibir. No es la
  contraentrega de Skydropx, porque nadie recauda por nosotros. Es un método de pago aparte, con
  su reserva y su confirmación a mano.

## Consecuencias

- Mientras la bandera esté apagada no cambia nada para el comprador.
- El checkout hace una consulta por cada carrito distinto, no una por sesión. Es una lectura de la
  base, sin terceros.
- Quien compra no sabe de antemano qué productos se pueden recoger: lo descubre en el checkout. Si
  eso se quiere anunciar en la ficha del producto, es otra decisión.
