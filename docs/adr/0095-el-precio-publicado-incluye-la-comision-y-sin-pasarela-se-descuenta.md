# ADR-0095: el precio publicado incluye la comisión de Wompi, y sin pasarela se descuenta

Fecha: 2026-10-26
Estado: aceptada. El negocio cerró las tres preguntas abiertas el mismo día (decisiones 6 a 8),
y el 27 de octubre decidió el redondeo de lo barato (decisión 9) y la forma de anunciar el
descuento (decisión 10).
Relacionados: `adr/0048` (Sistecrédito es una segunda pasarela), `adr/0094` (pago en el punto),
`docs/11-pagos-y-envios.md`, `CalculadoraDeMargen`.

## Contexto

Hoy cada venta se publica a un solo precio, y lo que el negocio recibe depende del medio de pago.
Las tarifas, leídas en los portales el 26 de octubre de 2026:

| Medio | Tarifa del plan | Costo real por venta |
|---|---|---|
| `WOMPI` | 2,65 % + $700 + IVA, por transacción exitosa | 3,1535 % + $833 |
| `SISTECREDITO` | 5,0 % de «gasto financiero por descuento de negociación» (cierre contable) | 5,0 % |
| `TRANSFERENCIA_MANUAL`, `PAGO_EN_PUNTO`, `CONTRAENTREGA` | — | sin comisión de pasarela |

En un bolso de $200.000 de costo, el tope de ganancia de `CalculadoraDeMargen` sugiere $230.000
para ganar $30.000. Si se paga con Wompi, la comisión es de $8.086 y la ganancia real queda en
$21.914; con Sistecrédito queda en $18.500. El tope protege el precio, pero no la ganancia.

El negocio preguntó si podía cobrarle la comisión aparte a quien eligiera un medio con pasarela.
La revisión del 26 de octubre encontró tres cosas:

- **Ley 1480, artículo 26:** el precio se informa con todos los impuestos y costos adicionales, y
  el consumidor solo está obligado a pagar el precio anunciado. Un recargo que aparece al elegir
  el medio de pago contradice el precio anunciado si no se informó antes. Según la prensa, la
  postura de la SIC es que el recargo se tolera si se avisa de forma visible y anticipada; no se
  encontró un concepto de la SIC con número que lo respalde.
- **El reglamento de comercios de Wompi (V4-2026)** prohíbe el recargo solo para el datáfono
  (cláusula 11.7.1, numeral 6: «Cobrar exactamente los mismos precios ofrecidos al público
  general sin recargo adicional por el uso del Dispositivo»). Para el pago en línea no dice nada.
- **El convenio de aliado de Sistecrédito no es público.** Cobrarle más a quien compra a crédito
  puede leerse como un costo de financiación que no está en el crédito.

Un descuento por pagar sin pasarela produce el mismo resultado económico sin ninguno de esos tres
riesgos: el precio anunciado es el máximo que alguien paga.

## Decisión

Aprobada por el negocio el 26 de octubre de 2026:

1. **Hay dos precios por variante.** El **precio de contado** es el que el negocio quiere recibir
   neto. El **precio publicado** se calcula a partir de él, incluye la comisión de Wompi y es el
   único que se anuncia como precio del producto:

   ```
   publicado = unidadDeMilSiguiente( (contado + fija × (1 + IVA)) / (1 − variable × (1 + IVA)) )
   ```

   Con las tarifas de hoy: `(contado + 833) / (1 − 0,031535)`, subido como dice la decisión 9. El
   bolso de $230.000 de contado se publica a **$239.000**; si se paga con Wompi, el negocio
   recibe $230.630.
2. **El tope de ganancia no cambia, y la comisión se suma después.** `CalculadoraDeMargen` sigue
   sugiriendo el precio de contado con el factor y los topes de $20.000 a $30.000 de hoy. El paso
   al precio publicado viene después y el tope no lo toca. Quitar el tope se descartó: un bolso de
   $200.000 volvería a sugerirse a $270.000, $31.000 más que con esta propuesta, y la comisión
   solo explica $9.000 de esa diferencia.
3. **El descuento es exacto por producto:** precio publicado menos precio de contado, por unidad.
   Se aplica con `TRANSFERENCIA_MANUAL`, `PAGO_EN_PUNTO` y `CONTRAENTREGA`. Con `WOMPI` se paga el
   precio publicado. Con `SISTECREDITO` también se paga el publicado, y el negocio absorbe la
   diferencia entre el 5 % y la comisión de Wompi (≈ 1,85 %): a quien compra a crédito no se le
   cobra nada encima.
4. **El precio lo calcula el servidor según el medio de pago** (regla 7). `CrearPedido` escribe en
   `LineaPedido.precioUnitario` el precio que corresponde al medio elegido, y el pedido guarda el
   descuento aplicado para poder auditarlo. El cliente nunca envía ni el precio ni el descuento.
   Como un pedido no cambia de medio de pago después de creado, el precio no se recalcula.
5. **Las tarifas son configuración, no constantes:** la variable, la fija y el IVA de Wompi van
   como variables de entorno con tipo y se validan al arrancar. Si Wompi cambia el plan, se
   cambian sin desplegar código, pero los precios publicados se recalculan (ver «Consecuencias»).
6. **Los precios que ya están en el catálogo son de contado.** La migración los copia al precio
   de contado y calcula el publicado con la fórmula de la decisión 1. Cuánto sube el precio de
   vitrina depende del valor, porque el cargo fijo y el redondeo a la unidad de mil pesan más en
   lo barato: $8.000 pasa a $10.000 (25 %), $50.000 a $53.000 (6 %), $100.000 a $105.000
   (5 %), $230.000 a $239.000 (3,9 %) y $1.000.000 a $1.034.000 (3,4 %). Una transferencia
   sigue dejando lo mismo que hoy.
7. **La comisión sobre el envío la absorbe el negocio.** El flete se cobra igual con cualquier
   medio de pago y no lleva comisión adentro: con un flete de $15.000 pagado con Wompi son unos
   $473 por pedido. No se le suma nada al flete, porque eso sería un recargo sobre el envío.
8. **Tecnología tiene un tope de ganancia de $400.000 y la comisión se suma después.** Hoy la
   configuración trae `precioMercado` y nadie le sugiere un precio: quien revisa lo fija en el
   panel. Desde ahora el **precio de contado sugerido** es el de mercado, pero sin ganarle al
   costo más de $400.000: `min(precioMercado, costo + 400.000)`; cuando manda el tope, se baja a
   la unidad de mil para no pasarse de él. El precio publicado sugerido
   sale de ese con la fórmula de la decisión 1. Tecnología no tiene ganancia mínima: el piso lo
   pone la regla 18 de `listas-de-proveedor`, que ya excluye lo que queda por debajo del costo o
   con menos de un 5 % de margen. Una configuración sin precio de mercado sigue sin sugerencia,
   y quien revisa fija el precio de contado. Como la ropa, la sugerencia no obliga.

9. **El publicado se sube siempre a la unidad de mil, también en lo barato** (27/10/2026). En
   lo barato el redondeo pesa más que la comisión: un cable de $8.000 se publica a $10.000, un
   25 % más, cuando la comisión sola daría $9.200. Se probó subir a la centena por debajo de
   $100.000 y el negocio volvió a la unidad de mil el mismo día: la vitrina va en miles
   redondos en todo el catálogo, y la diferencia en lo barato queda como margen.
10. **La vitrina no muestra un segundo precio, muestra el ahorro** (27/10/2026). El artículo 26
   de la Ley 1480 dice que, cuando aparezcan dos o más precios, el consumidor solo está
   obligado a pagar el más bajo. La ficha, la tarjeta y el carrito dicen «Ahorras $X pagando por
   transferencia, en el punto o contra entrega»; el desglose con el descuento solo aparece en el
   checkout, con el medio ya elegido. Si basta es el punto 12 de `docs/14`.

## Consecuencias

- **La vitrina muestra el precio y el ahorro:** «$239.000» y, debajo, «Ahorras $9.000 pagando
  por transferencia, en el punto o contra entrega» (decisión 10). El checkout enseña el total
  que corresponde al medio antes de confirmar. Todo pasa por Transloco, en español e inglés.
- **Los términos y condiciones cambian.** Hay que declarar el descuento, a qué medios aplica y
  que el precio anunciado es el máximo. Eso se hace con la skill `textos-legales-comerciales` y
  luego `vacios-legales-del-sitio`, nunca a mano. Si el descuento se trata como una promoción,
  aplica también el régimen de promociones y ofertas de la Ley 1480.
- **El cargo fijo de $833 va dentro de cada producto.** En un carrito de tres productos pagado
  con Wompi, el negocio recupera tres veces un cargo que Wompi cobra una sola vez por transacción.
  El margen queda un poco a favor del negocio y no al revés, así que se acepta.
- **El monto mínimo de Sistecrédito** (`MetodosDePagoDisponibles`) se compara contra el total
  publicado, que es lo que Sistecrédito cobra.
- **El reintegro** (retracto, garantía, recogida no retirada) devuelve lo que se pagó, que ya es
  `precioUnitario`. No cambia.
- **Un cambio de tarifa mueve los precios publicados.** Recalcularlos es una tarea explícita del
  panel, no algo que pase solo: un precio anunciado no debe cambiar sin que una persona lo vea.
- **La contraentrega recibe el descuento.** En la cotización de 99minutes el recaudo no lleva
  recargo (`docs/13-skydropx-capacidades.md`), pero eso solo cubre esa transportadora. Si otra
  cobra por recaudar, este descuento lo paga el margen.
- **En tecnología, el precio publicado sugerido queda por encima del de mercado** cuando el tope
  no actúa: un equipo con mercado de $1.000.000 se sugiere publicado a $1.034.000 y de contado a
  $1.000.000. Es lo que decidió el negocio: el precio de mercado compite con el de contado, que
  es el que se muestra pagando sin pasarela.
- **El tope de $400.000 también es configuración**, junto a los de la ropa
  (`proveedores.ganancia`), pero con su propia clave, porque son políticas distintas.

## Cómo se construye

Un caso de uso a la vez, cada uno con su prueba y en su propio PR:

1. El dominio: `ComisionDePasarela` (la fórmula de la decisión 1) y el precio de contado en
   `Variante`. La migración copia el precio actual al de contado y calcula el publicado.
2. `CalculadoraDeMargen` devuelve el precio de contado. La sugerencia de tecnología, con su tope.
3. `CrearPedido` elige el precio según el medio de pago y congela el publicado por línea, del
   que sale el descuento.
4. La vitrina y el carrito muestran el ahorro, y el checkout el descuento, en español y en inglés.
5. Los términos y condiciones, con la skill de textos legales y luego la de vacíos legales.
