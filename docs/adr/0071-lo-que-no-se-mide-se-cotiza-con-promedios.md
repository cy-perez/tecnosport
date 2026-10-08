# ADR-0071 — Lo que no se mide se cotiza con promedios, en una bolsa

**Fecha:** 2026-10-07
**Estado:** aceptado. Reemplaza en parte a `ADR-0046` (la ropa, el calzado y los
bolsos sin medir ya no van solo con recogida) y a la decisión del 11 de
septiembre de 2026 de "un bulto por unidad" (lo promediado viaja junto).

## Contexto

Desde el `ADR-0046`, una variante sin peso ni medidas se vende, pero solo con
recogida en el punto. El ADR dejó escrito el costo: *"un producto sin medir vende
menos"*, porque quien compra desde Cali y no puede recibir a domicilio
probablemente no compra. Y la salida que proponía era medir.

Medir funciona para la tecnología: el fabricante publica la caja (JBL lo hace) o
se pesa una vez y vale para siempre. **No funciona para la ropa, el calzado y los
bolsos**: son muchas referencias, cambian con cada lista de proveedor, y nadie va
a pasar cada blusa por la báscula. En la práctica, toda esa mercancía quedaba con
solo recogida.

Además, el negocio decidió cómo va a despachar mientras el flujo con Skydropx no
esté automatizado: **las guías se crean a mano** en el panel de la plataforma
("Cotizar y crear", `app.skydropx.com/co/es-CO/quotations/new`). Ese formulario
pide origen, destino, tipo de empaque, largo, ancho, alto, **peso en kilos
enteros** y valor declarado. Si el sitio cotiza con datos distintos de los que se
escriben ahí, el flete que paga el comprador no es el que cobra la plataforma, y
la diferencia la pone el negocio en cada pedido.

## Decisión

Las cifras son del negocio, entregadas el 7 de octubre de 2026; este ADR decide
cómo se usan, no cuánto valen.

1. **Peso promedio por categoría, solo para ropa, calzado y bolsos.** Cada
   categoría hoja de esas tres líneas puede tener un peso en gramos (Ropa › Dama ›
   Jeans: 700 g; Calzado › Unisex: 700 g; Bolsos › Caballero › Morrales: 1 kg…).
   La tecnología no promedia: un celular y un proyector no tienen nada que
   promediar, y su ficha publica la caja. Lo decide
   `PesoDeReferencia.admiteLaLinea`, un `switch` exhaustivo sin `default`: una
   línea nueva no compila hasta que alguien decida.
2. **Medidas transversales de la bolsa: 40 × 30 × 10 cm.** Una sola para las
   tres líneas, porque lo que se mide es la bolsa de despacho y no la prenda.
3. **Todo lo promediado de un pedido va en una sola bolsa**, al final de la lista
   de bultos: medidas de referencia, peso igual a la suma de los promedios, valor
   declarado igual a la suma de los precios. Lo medido —la tecnología, o una
   prenda que alguien sí pesó— sigue en un bulto por unidad con sus medidas
   reales. **Un pedido mixto son dos paquetes**: la caja y la bolsa.

   **Salvo que la bolsa pase del techo asegurable** (`ADR-0036`). El techo se
   valida por bulto, y antes cada unidad era su bulto: doce pares de tenis de
   450.000 eran doce bultos asegurables, y en una sola bolsa serían 5.400.000 y el
   pedido quedaría solo con recogida. La bolsa se cierra cuando la siguiente
   unidad la llevaría por encima del techo y se abre otra con las mismas medidas.
   Lo encontró la revisión adversarial del mismo día.
4. **Todo peso sale redondeado hacia arriba al kilo entero**
   (`Paquete.alKiloSiguiente`), también el de lo medido: es lo que acepta el
   formulario. La suma va antes del redondeo: dos jeans son 1.400 g y una bolsa de
   2 kg, no dos kilos por prenda.
5. **La medida real manda.** Una variante con paquete propio viaja con él aunque
   su categoría tenga promedio.
6. **Sin promedio, como antes.** Una categoría sin peso, o una base sin medidas
   de la bolsa, deja esas variantes en el `ADR-0046`: solo recogida, con `409
   ARTICULO_SIN_MEDIDAS`.
7. **Tipo de empaque `5H4`, "Saco (bolsa) de película de plástico"**, para todo
   lo que se despacha, en lugar de `4G` (caja de cartón). Hoy solo lo usa la
   emisión por API, que está apagada; queda escrito para cuando vuelva, y **no se
   ha probado contra la plataforma**: la emisión real solo ejerció `4G` (`docs/13`
   §6.10). Lo primero al encenderla es una guía de prueba.
8. **La emisión por API se apaga en el servidor**, no solo en el panel:
   `SKYDROPX_EMISION_AUTOMATICA`, por omisión `false`, hace que
   `EmitirGuiaDePedido` responda `409` antes de cotizar. El botón "Emitir guía
   con la transportadora" también se oculta (`EMISION_AUTOMATICA = false` en
   `lista-pedidos-admin.page.ts`), pero ocultarlo no bastaba: una pestaña con el
   código anterior, un `curl` con token o la app móvil llegan igual al endpoint, y
   con las guías creadas a mano eso paga dos para el mismo pedido. La guía se
   registra con el formulario de "guía emitida por fuera", que sigue igual. El
   camino por API no se borra: la intención es automatizar el flujo y volver a
   encenderlo, y entonces van las dos banderas juntas.
9. **El panel muestra los paquetes de cada pedido** listos para copiar en el
   formulario —peso en kilos, medidas, valor declarado y contenido—, en la fila
   del pedido en preparación (`GET /api/v1/admin/envios/paquetes/{pedidoId}`).
   Salen del mismo método que usaría la emisión
   (`ArmadorDeBultos.armarParaDespachar`). Sin esto, la contraentrega se
   declaraba de cabeza, y escribir "el total del pedido" en cada uno de dos
   paquetes cobra el doble en la puerta: el comprador rechaza, el negocio paga ida
   y vuelta, y el rechazo le cierra la contraentrega a ese comprador.

Las cifras se cambian desde el panel, en **Pesos y medidas de envío**
(`/admin/envios/referencias`), sin desplegar nada. La `V89` solo siembra el
punto de partida, **por slug**, porque los ids de las categorías no coinciden
entre ambientes; un slug que no exista no inserta nada y esa categoría aparece en
el panel sin peso.

## Cómo llenar el formulario de Skydropx para que cuadre

Lo que el sitio cotizó sale de exactamente estos datos, así que la guía manual
tiene que llevarlos. **No hay que calcularlos**: la fila del pedido en el panel
los muestra, paquete por paquete, en "Paquetes para crear la guía".

| Campo | Qué va |
|---|---|
| Origen | La dirección de la tienda guardada en Skydropx (Cra. 26C # 38B-31, apto. 401, La Milagrosa, Medellín), la misma de `ORIGEN_*` |
| Paquete | Saco (bolsa) de película de plástico |
| Largo × ancho × alto | Bolsa: las medidas de referencia del panel. Producto medido: las suyas |
| Peso | La suma, redondeada hacia arriba al kilo |
| Valor declarado | El de cada paquete, como lo muestra el panel. Pago en línea: la suma de los precios de lo que lleva. **Contraentrega: además lleva repartido el flete**, y la suma de todos los paquetes es el total del pedido, porque la plataforma cobra en la puerta la suma de lo declarado (`ADR-0037`). Nunca el total en cada paquete |

Un pedido mixto se crea con **dos paquetes**.

## Consecuencias

**A favor:**

- La ropa, el calzado y los bolsos se venden a domicilio sin medir nada.
- La cotización del sitio y la guía manual salen de los mismos datos.
- Cambiar una cifra es una fila en el panel, no un despliegue.

**En contra, y hay que decirlo:**

- **El volumen pesa más que la prenda.** La plataforma cobra peso volumétrico
  (una caja de 30×25×10 de 1 kg se cotizó como 3 kg, `docs/13` §6). Con 40 × 30
  × 10 cm, cualquier bolsa se cotiza como de unos 5 kg aunque lleve una camiseta
  de 300 g: el redondeo al kilo casi nunca decide la tarifa, la deciden las
  medidas. Si la bolsa real es más delgada, bajar el alto en el panel baja el
  flete de casi todos los pedidos de ropa.
- **Varias prendas en la misma bolsa no cambian sus medidas.** Diez camisetas se
  cotizan con 40 × 30 × 10 cm igual que una. Si un pedido grande no cabe, la guía
  manual se crea con lo real y la diferencia la pone el negocio.
- **Un promedio se queda corto a veces.** Unos tenis de 900 g en una categoría de
  700 se cotizan de menos. Cuando pasa con frecuencia en una categoría, se sube su
  promedio; cuando es un producto, se mide y gana su propia medida.
- **El sistema no se entera de la guía manual** más que por el número que se
  registra a mano: el seguimiento automático y la conciliación del costo real
  dependen de que ese número se registre.
- **Lo cotizado no se congela.** El pedido guarda la tarifa, pero no el peso ni
  las medidas de la bolsa, y el panel arma los paquetes con las referencias de
  hoy. Si un promedio cambia entre la compra y el despacho, la guía sale con el
  nuevo y la diferencia contra lo que pagó el comprador no queda explicada en
  ninguna parte. Y quitarle el peso a una categoría deja sin paquetes —con `409
  ARTICULO_SIN_MEDIDAS`— a los pedidos ya pagados de esa categoría.
- **Las cifras del panel no tienen tope.** Un 1 escrito creyendo que son kilos
  cobra fletes de menos en silencio. No se inventó un máximo: sería un dato de
  negocio, y `Paquete` decidió hace tiempo no tenerlo.

**Qué reabre esta decisión:** automatizar la emisión. Entonces el empaque y el
peso redondeado ya viajan bien por API, pero hay que decidir si la bolsa
consolidada se emite como un solo paquete —la plataforma empareja paquetes con
bultos por posición, y hoy la bolsa va al final— y volver a encender el botón.
