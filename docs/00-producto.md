# Producto

## Qué es

Tienda en línea propia de TecnoSport. Vende al detal, con pago en línea, pago
contraentrega y envío a todo Colombia —cotizado por destino, cobrado aparte del
precio del producto—, además de recogida sin costo en el punto de Medellín.

TecnoSport es un negocio real con diez años de oficio: distribuidor de tecnología
y ropa y calzado deportivo en Medellín. Persona natural, NIT 1054994043-9,
Cra. 26C #38B-31, Medellín. Teléfono y WhatsApp 310 420 9655. Correo
contact@tecnosport.co. Dominio tecnosport.co.

## Alcance de la fase 1

Sí entra:

- Catálogo de tres líneas: ropa y calzado deportivo, bolsos, celulares.
- Búsqueda, filtros y orden.
- Ficha de producto con imagen principal, galería y **visor de rotación 360**.
- **Asistente de captura de fotos** para producir los fotogramas del visor desde
  un teléfono, sin estudio fotográfico.
- Carrito persistente.
- Checkout con pago en línea por Wompi, transferencia y **contraentrega**.
- **Cotización de envío por destino con Skydropx**, o recogida sin costo en el
  punto, y **seguimiento del envío** visible para el comprador.
- Compra como invitado; cuenta opcional.
- Panel de administración: productos, variantes, existencias, imágenes, pedidos.
- Correos transaccionales: confirmación, pago aprobado, despacho, entrega.
- Español e inglés.
- Modo claro y oscuro, y controles de accesibilidad.

No entra en la fase 1, y no se construye anticipadamente:

- Mayoreo, listas de precio por cliente y cotizaciones.
- Facturación electrónica DIAN automática. Se emite por fuera; queda el puerto.
- App móvil. El backend se diseña para servirla, pero no se construye ahora.
- Devoluciones y cambios gestionados en línea. Se atienden por WhatsApp.
- Cupones, puntos y programas de fidelidad.

## Actores

| Actor | Qué puede hacer |
|---|---|
| Visitante | Ver catálogo y precios, buscar, filtrar, girar el visor 360, armar carrito, comprar como invitado |
| Cliente registrado | Lo anterior, más historial de pedidos, direcciones guardadas y seguimiento |
| Administrador | Productos, variantes, precios, existencias, captura y carga de imágenes, pedidos, despacho, confirmación de recaudo |

La autenticación no desaparece por vender al detal. Los precios son públicos y no
hay muro de registro, pero el panel administrativo la exige y el checkout
identifica al comprador, aunque sea solo por correo.

## Recorridos que tienen que funcionar

1. **Comprar como invitado.** Llega a una ficha de producto, gira la imagen 360,
   elige talla, agrega al carrito, paga con Nequi y recibe el correo. Sin crear
   cuenta.
2. **Comprar un celular.** Elige capacidad y color, ve que hay dos unidades, paga
   con tarjeta, y el sistema le asigna una unidad concreta con su IMEI.
3. **Comprar contraentrega.** Elige contraentrega, el sistema valida —con la
   cotización del destino— que hay transportadora con recaudo y que el monto está
   dentro del límite, confirma el pedido sin cobrar, y el cobro ocurre en la
   entrega, en efectivo y por el total con flete incluido.
4. **Comprar con envío cotizado.** Escribe la dirección, el servidor cotiza y le
   muestra el costo del envío y el plazo estimado por separado del subtotal,
   paga el total, y después sigue el paquete desde el enlace del correo hasta que
   dice entregado.
5. **Comprar y recoger en el punto.** Elige recogida, el costo de envío queda en
   cero, y recibe un código de retiro en vez de una guía.
6. **Publicar un producto con 360.** El administrador entra desde su teléfono al
   asistente de captura, toma los fotogramas guiados por la silueta y el nivel,
   el cliente los recorta y los sube, y el producto queda publicado con el visor
   funcionando.
7. **Pago rechazado.** Falla el pago, el inventario reservado se libera, el pedido
   queda en pago fallido y se puede reintentar desde el enlace del correo.

## Reglas de negocio que el código debe respetar

- **Los precios se muestran con IVA incluido.** Lo exige el Estatuto del
  Consumidor. La factura desglosa; la vitrina no.
- **Precio, existencia y costo de envío se recalculan en el servidor antes de
  cobrar.** Nunca se confía en lo que envía el navegador.
- **El precio publicado es un precio base, sin flete.** El costo de envío se
  cotiza contra el destino real con Skydropx y se cobra aparte, informado por
  separado en el resumen del pedido antes de pagar (`adr/0021`). Lo exige el
  artículo 50 de la Ley 1480 de 2011, además de ser lo que el negocio decidió.
- **La recogida en el punto no paga envío**, y el checkout lo dice: es la única
  forma de comprar sin flete, así que el ahorro se muestra junto a la opción.
- **Sin tarifa cotizada no hay envío a domicilio.** Si el destino no tiene
  cobertura o el proveedor no responde, se ofrece solo la recogida. No se inventa
  un flete ni se aplica una tarifa de respaldo.
- **Una variante sin peso ni dimensiones no se publica.** Sin paquete no hay
  cotización.
- **No se vende lo que no hay.** El inventario se reserva al iniciar el pago y se
  descuenta al confirmarlo. Una reserva de pago en línea vence a los 30 minutos;
  una de contraentrega dura hasta el despacho.
- **Los celulares se manejan por unidad serializada.** Cada equipo tiene IMEI y se
  asocia al pedido en el despacho, no antes.
- **Un producto no se publica sin imagen principal.** El set 360 es opcional, pero
  si existe debe estar completo: un set a medias no se muestra.
- **Todo cambio de estado de un pedido queda registrado** con fecha, actor y
  motivo. No se sobrescribe historia.

## Cómo se mide que salió bien

- La ficha de producto carga en menos de 2,5 segundos en 4G, medido como LCP, y
  el visor 360 no bloquea esa métrica.
- El catálogo indexa en Google en español e inglés sin contenido duplicado.
- Cero fallos de contraste y foco visible en todos los controles.
- Un pedido pagado nunca deja el inventario inconsistente.
- La tasa de pedidos contraentrega rechazados en la entrega se puede medir, y por
  eso se registra.
