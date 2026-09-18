# ADR-0040 — El flete cobrado no se grava

Fecha: 2026-09-18
Estado: aceptado, **por decisión de negocio y sin concepto de contador**
Relacionados: `adr/0021`, `docs/02-modelo-datos.md`, `docs/12-legales-de-envio.md` §4

## Contexto

Desde `adr/0021` el precio publicado de un producto es un precio final con IVA y **sin flete**, y el
costo de envío se cotiza contra el destino real y se cobra aparte. El total del pedido es la suma de
las líneas más el envío.

Lo que nunca se decidió es **qué pasa con el IVA de esa línea de envío**, y el 14 de septiembre de
2026 se descubrió que la pregunta se había contestado mal porque eran dos:

- El **servicio de transporte de carga comprado suelto** está **excluido** de IVA. Es cierto, es lo
  que se encuentra al buscar, y es la razón por la que el `rate.total` que cobra Skydropx no trae
  impuesto dentro.
- El **flete que el vendedor le recobra al comprador dentro de una venta gravada** es otra cosa. El
  **artículo 447 del Estatuto Tributario** manda incluir en la base gravable los "acarreos" y demás
  erogaciones complementarias *"aunque se facturen o convengan por separado y aunque, considerados
  independientemente, no se encuentren sometidos a imposición"* — y esa última frase describe
  exactamente este caso. El **Concepto DIAN 4945 de 2025** lo confirma para el transporte que
  contrata el vendedor para entregar, incluso subcontratado a un tercero.

La distinción que sí eximiría es que **el comprador contrate el transporte por su cuenta** con
alguien ajeno a la venta. No es lo que hace este sitio: cotizamos, el servidor elige la tarifa más
económica y se la cobramos dentro del pedido.

### Los tres escenarios, con números

Un producto de 250.000 (precio final publicado) y un flete cotizado de 7.850 — la cifra de la última
guía emitida contra la cuenta real:

| | Paga el comprador | Se le paga a Skydropx | IVA declarado | Queda del flete |
|---|---|---|---|---|
| **A. No se grava** | 257.850 | 7.850 | 39.916 | **0** |
| **B. Se grava y se suma** | 259.342 | 7.850 | 41.408 | **0** |
| **C. Se grava y se absorbe** | 257.850 | 7.850 | 41.169 | **−1.253** |

Lo único visible para el comprador entre B y A son 1.492 pesos en la línea de envío. Las tarifas
vivas de la cuenta van de 5.991 a 9.897, así que la horquilla del impuesto por pedido a domicilio
está entre 1.138 y 1.880.

**C no es una opción que alguien elija: es dónde se cae por omisión** si el impuesto aplica y nadie
lo sumó al cobrar. Es el estado en el que el sistema llevaba desde la Fase 7 sin saberlo.

## Decisión

**El flete que se le cobra al comprador no lleva IVA.** Se cobra el valor cotizado tal cual, sin
sumarle el 19% y sin desglosarlo por dentro. Es el escenario A.

**La decisión es del negocio y no tiene concepto de contador detrás.** Queda escrito así, con todas
las letras, porque un documento que diga "confirmado por el contador" cuando ninguno lo revisó es
peor que no tener el documento: le da a quien lo lea después una certeza que nadie produjo.

Por eso este ADR entra en la lista de verificación antes de publicar de
`docs/12-legales-de-envio.md`, junto a los puntos que espera el abogado. No bloquea desplegar —no
hay nada que construir ni que deshacer— pero tampoco está cerrado del todo.

### Por qué se decide ahora y no cuando llegue la respuesta

Porque las tres salidas se ven distintas en el código y **A es la única que no cuesta nada**: es lo
que el sistema ya hace. Dejar la pregunta abierta tenía un costo real —un `TODO` en el modelo de
datos, otro en `adr/0021`, una fila en la tabla de datos pendientes y un texto de interfaz
estrechado a la mitad— y ese costo se pagaba igual estuviera la respuesta o no.

Y porque la ventana barata se está cerrando: hoy no hay un solo pedido real. Si esto se reabre con
historia encima, hay que arreglar hacia atrás pedidos que ya se cobraron.

## Alternativas rechazadas

- **B: sumarle el 19% al valor cotizado.** Es la que más se ajusta a la lectura del 447 y a lo que
  dice el Concepto 4945, y es la única de las tres que deja el flete en cero para el negocio si el
  impuesto aplica. Se rechaza por decisión comercial: sube el flete visible ~19% en el momento en
  que el comprador decide, y el negocio prefiere no ponerle ese peso al checkout mientras no haya un
  concepto que lo obligue. **Es la alternativa a la que se vuelve si esto se reabre**, no C.
- **C: absorberlo del margen.** Nadie la elige; es donde se cae por omisión. Si el impuesto aplica,
  hoy estamos en C sin haberlo decidido — y lo peor de C no es perder 1.253 por pedido, es que
  **nada en el sistema lo muestra**: `pedido.costo_envio` guarda un solo número y no hay forma de
  ver la fuga leyendo un pedido.
- **Dejarlo abierto y construir el desglose "por si acaso"**, con el impuesto en cero y una
  configuración que lo enciende. Se consideró en serio y se descartó: dos columnas que siempre valen
  base y cero son una invitación a que alguien las sume mal, y el día que se enciendan hay que tocar
  el checkout, el correo y la pantalla de estado igual. El trabajo no se ahorra, solo se adelanta a
  ciegas.

## Consecuencias

- **No cambia una línea de código.** `Pedido.total()` sigue siendo `subtotal() + costoEnvio()`, y
  `costoEnvio()` sigue siendo el `costo` de la `TarifaEnvio` tal cual.
- **`pedido.costo_envio` se queda como una sola columna, y ahora eso es una decisión.** Hasta hoy
  era una omisión con un daño anotado —"de lo ya cobrado no se puede separar base e impuesto para
  facturar"—. Con A no hay nada que separar: el flete es un valor sin impuesto dentro. La columna
  dice la verdad completa.
- **`LineaPedido.tasaIva` sigue congelándose por línea y sigue sin usarse para calcular nada.** Eso
  no cambia con este ADR y no es un defecto: es dato de factura guardado para cuando exista la
  factura. Lo que sí queda claro es que **la línea de envío no tiene tasa**, y no porque se haya
  olvidado.
- **El texto del checkout no se toca.** Dice "Los precios de los productos incluyen IVA" y calla
  sobre el flete, desde `96a7c69`. Ampliarlo a *"el costo de envío corresponde a un servicio
  excluido de IVA"* sería cierto solo en el escenario A; callar es cierto en los tres. Un sitio
  público no es donde se afirma una posición tributaria que ningún contador firmó.
- **La cláusula publicada de los T&C tampoco cambia**: dice que los precios incluyen el IVA
  aplicable y que el envío va aparte, y las dos siguen siendo verdad bajo A.
- **El riesgo queda del lado del negocio, y es cuantificable**: si la DIAN lee el 447 como lo lee
  este documento, cada pedido a domicilio ya cobrado debe el 19% de su flete, y de los que estén
  cobrados para entonces no se podrá reconstruir el desglose.

## Lo que esto NO arregla

**Nadie ha confirmado que el negocio sea responsable de IVA.** No está escrito en ningún documento
del proyecto, y el sistema ya lo asume por todas partes: el catálogo sembrado lleva `tasa_iva = 0.19`
en todas sus variantes, `Variante` valida la tasa entre 0 y 1, y el sitio publica que los precios la
incluyen. Si el negocio estuviera en régimen simple o como no responsable, esa columna estaría
mintiendo y esta decisión sería irrelevante — no por correcta, sino por no venir al caso. **Es una
pregunta más básica que la de este ADR y sigue sin respuesta.**

**Y el prorrateo de un carrito mixto sigue sin modelar.** `docs/02-modelo-datos.md` decide, a
propósito, que el costo de envío no se reparte entre las líneas: repartirlo obliga a redondear N
veces y a decidir qué pasa con el sobrante. Correcto mientras todas las líneas tengan la misma tasa,
y hoy la tienen. El día que entre un producto excluido de IVA al catálogo —el modelo admite
`tasa_iva = 0.00` y el documento lo contempla—, un flete que cubre a la vez mercancía gravada y
excluida tendría que prorratearse para saber cuánto de él entra en la base gravable. Bajo A no hay
base que repartir, así que el problema no existe; **vuelve a existir el día que se elija B**, y un
desglose de un solo par de columnas no lo soportaría.

## Qué haría falta para reabrirlo

Cualquiera de estas tres, y ninguna es "que alguien vuelva a leer el artículo 447":

1. **Un concepto de contador** que diga que el flete recobrado integra la base gravable. Es el
   camino esperado, y la salida sería B, no C.
2. **Un requerimiento o una glosa de la DIAN** sobre pedidos ya facturados.
3. **Que cambie el modelo de cobro**: si algún día el comprador contratara el transporte por su
   cuenta con un tercero ajeno a la venta, la exclusión aplicaría de frente y esta decisión pasaría
   de ser una apuesta a ser la respuesta correcta. No es lo que hace el sitio hoy ni hay plan de que
   lo haga.

Si se reabre, el trabajo está acotado y escrito: `TarifaEnvio` gana el desglose, una migración parte
`pedido.costo_envio` en base e impuesto, y el texto del checkout vuelve a la frase completa que tenía
antes de `96a7c69`.
