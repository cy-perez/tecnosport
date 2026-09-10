# Legales del envío cotizado, la recogida en el punto y el seguimiento

Este documento sale de las dos skills legales del proyecto y tiene dos mitades
que conviene no confundir:

- **Los textos** que hay que publicar cuando el envío deje de estar incluido en el
  precio (`.claude/skills/textos-legales-comerciales`). Están redactados, en
  español e inglés, listos para pegar en los JSON de Transloco.
- **La auditoría** de lo que el sistema hace hoy contra lo que esos textos y la
  ley exigen (`.claude/skills/vacios-legales-del-sitio`), con ruta y línea.

**Regla de publicación, y es la parte importante:** las cláusulas de la sección 3
se publican **en el mismo commit que encienda la cotización** (`adr/0021`), con la
fecha de versión nueva. Ni antes ni después. Hoy el sitio cobra de verdad con el
envío incluido; publicar el texto nuevo antes de que el código cambie lo volvería
falso en la otra dirección, que es el mismo problema con otro signo.

Estos son **borradores**. Los revisa un abogado colegiado antes de publicarlos, y
al final hay una lista corta de los puntos donde de verdad hace falta.

**Resumen, sin rodeos:** el sitio como está hoy puede seguir vendiendo, porque su
texto y su código dicen lo mismo; **encender la cotización sin los tres cambios
del nivel 1 no se puede**, porque el mismo día que se cobre el primer flete el
sitio estará cobrando un cargo adicional que su propio documento promete que no
existe, sin mostrar el total antes de pagar. Los dos primeros se cierran con el
cambio; el tercero es un defecto que ya está en producción.

---

## 1. La norma, verificada antes de escribir

Consultado el 8 de septiembre de 2026 en fuente oficial. No se cita nada de
memoria; donde no se pudo confirmar el número de artículo, la obligación va sin
cita.

| Qué exige | Norma | Cómo pega aquí |
|---|---|---|
| Antes de finalizar la transacción, un resumen del pedido con el precio individual de cada bien, el precio total, **los costos adicionales de envío informados de forma adecuada y separada**, y la suma total a pagar | **Ley 1480 de 2011, art. 50** | Es la obligación que **sostiene** el modelo nuevo: cobrar el flete aparte no es un riesgo legal, es lo que la norma describe. Lo que sí es riesgo es no mostrar la cifra antes de pagar |
| Los costos adicionales al precio —transporte, seguros, estudios de crédito— se informan de forma adecuada, con su razón y su valor | Ley 1480 de 2011 (deber de información de precios) | El flete lleva su valor exacto, no un "más gastos de envío" |
| Retracto: 5 días hábiles desde la entrega, sin justificar | **Ley 1480 de 2011, art. 47** | Cuelga de la fecha de entrega, que ahora la reporta la transportadora |
| Reintegro del dinero: máximo **15 días calendario** en comercio electrónico desde que se ejerce el derecho y se cumplen las obligaciones del consumidor, aplicado al mismo instrumento de pago o al medio acordado, y el plazo **obliga a todos los intervinientes, incluida la entidad financiera** | **Art. 47, modificado por la Ley 2439 de 2024** | Ya estaba en los términos publicados y no cambia |
| **"Los costos de transporte y los demás que conlleve la devolución del bien serán cubiertos por el consumidor"** | **Ley 1480 de 2011, art. 47** | Resuelve el `[[QUIÉN PAGA EL FLETE DE DEVOLUCIÓN]]` que quedó abierto en la Fase 6: **el flete de la devolución lo paga el comprador** |
| Los gastos que genere la devolución del dinero, incluidos los costos financieros, los asume quien vendió, no el consumidor | Concepto de la SIC sobre el art. 47 | La comisión de la pasarela por devolver la plata no se le descuenta al comprador |
| Reversión del pago con causales tasadas, y reversión parcial cuando la compra fue de varios productos | Ley 1480 de 2011, art. 51, y Decreto 587 de 2016 | Ya está en los términos. Con flete aparte aparece la pregunta de si el flete se reversa; ver sección 7 |
| La información mínima al consumidor debe estar en castellano, y en los contratos se usa el castellano | Ley 1480 de 2011, arts. 23 y 37.1 | Ya resuelto en la Fase 6 con la nota de traducción de cortesía |
| Transferencia internacional de datos: régimen propio, con lista de países de nivel adecuado | Ley 1581 de 2012 y circular de la SIC sobre nivel adecuado de protección | **México aparece en la lista de países con nivel adecuado** de la SIC. Skydropx es mexicana; eso no cierra el análisis, lo simplifica |
| La publicidad obliga | Ley 1480 de 2011 | "Recogida sin costo" y un plazo de entrega en pantalla son promesas exigibles |

Lo que **no** cambió y conviene decirlo: la Ley 1581 de 2012 sigue sin reforma
vigente, y el retracto sigue en 5 días hábiles. Lo que la Ley 2439 de 2024 tocó
fue el plazo del reintegro.

---

## 2. Mapa de lo que cambia en cada documento

| Documento | Numeral | Qué dice hoy | Qué tiene que decir |
|---|---|---|---|
| Términos y condiciones | 4. Productos, imágenes y precios | "El precio publicado incluye además el costo del envío a cualquier parte del país: no hay cobros adicionales al final del proceso" | Precio base sin flete, flete cotizado por destino y visible antes de pagar, total con las dos cifras |
| Términos y condiciones | 7. Pago contraentrega | Disponibilidad decidida por el sistema | Añade: se cobra el **total con el flete incluido**, y **solo en efectivo** |
| Términos y condiciones | 8. Envío y entrega | Despacho nacional con el término legal supletivo de 30 días —**se decidió no prometer plazo propio**—, y el punto de retiro con su dirección | Cotización por destino, plazo estimado del transportador **como estimado**, recogida sin costo, destinos sin cobertura |
| Términos y condiciones | 9. Derecho de retracto | `[[QUIÉN PAGA EL FLETE DE DEVOLUCIÓN]]` | El comprador paga el transporte de la devolución (art. 47); se le reintegra lo que pagó, flete de ida incluido |
| Términos y condiciones | 11. Reversión del pago | Causales y plazo | Sin cambios de fondo; ver el punto para abogado sobre el flete |
| Política de datos | 7. Finalidades | "Procesar, confirmar y despachar tu pedido" | Añade cotizar el envío, generar la guía y seguir el paquete |
| Política de datos | 8. Encargados y terceros | `[[TRANSPORTADORA]]` sin resolver | Skydropx como plataforma logística, y las transportadoras que ejecutan la entrega |
| Política de datos | 9. Transferencia internacional | Nube genérica | Añade la plataforma logística y su país |
| Política de cookies | — | — | Sin cambios: Skydropx no pone cookies en el sitio |

Los numerales **no se renumeran**. Un documento legal se cita por numeral, y
correr la numeración rompe cualquier referencia anterior, incluida la de una
compra ya hecha.

---

## 3. Cláusulas redactadas

Van al archivo `apps/web/src/assets/i18n/scopes/legales/{es,en}.json`, en la
sección que corresponde, reemplazando el párrafo indicado. **La versión española
es la que rige**; la inglesa es traducción de cortesía y el sitio ya lo dice.

Encajan en la estructura que la plantilla ya pinta —`parrafos`, `lista` y
`cierre` (`features/legales/presentation/documento/documento-legal.page.html:25-37`)—
así que ninguna cláusula nueva necesita un campo que nadie renderice. Esa
comprobación no es de estilo: un párrafo escrito en el JSON que la plantilla no
pinta, jurídicamente no está.

**Los marcadores `[[ ]]` están traducidos**, y hay que resolver los dos lados a la
vez: `[[PLAZO DE ENTREGA REAL]]` es `[[ACTUAL DELIVERY TIME]]` en inglés,
`[[TRANSPORTADORA]]` es `[[COURIER]]`, `[[QUIÉN PAGA EL FLETE DE DEVOLUCIÓN]]` es
`[[WHO PAYS RETURN SHIPPING]]`. Resolver uno y olvidar el otro deja dos
documentos que prometen cosas distintas, y los dos se leen.

### T&C 4. Productos, imágenes y precios — reemplaza el segundo párrafo

**es:**

> Todos los precios se muestran en pesos colombianos (COP) e incluyen el IVA
> aplicable. El precio de cada producto no incluye el costo del envío: ese valor
> depende de la ciudad de destino y del tamaño y peso de lo que compras, y se
> calcula al momento de tu compra. Antes de pagar te mostramos el detalle
> completo: el precio de cada producto, el subtotal, el costo del envío por
> separado y el total que vas a pagar. Si eliges recoger tu pedido en nuestro
> punto de Medellín, no pagas costo de envío.

**en:**

> All prices are shown in Colombian pesos (COP) and include applicable VAT. The
> price of each product does not include the shipping cost: that amount depends
> on the destination city and on the size and weight of your order, and is
> calculated at the time of purchase. Before you pay, we show you the full
> breakdown: the price of each product, the subtotal, the shipping cost listed
> separately, and the total you will pay. If you choose to pick up your order at
> our Medellín location, you pay no shipping cost.

### T&C 7. Pago contraentrega — añade al final

**es:**

> Cuando pagas contra entrega, el valor que cobra la transportadora es el total
> del pedido, es decir la mercancía más el costo del envío. Las transportadoras
> solo reciben efectivo, así que ten a mano el valor exacto.

**en:**

> When you pay on delivery, the amount the carrier collects is the order total,
> that is, the goods plus the shipping cost. Carriers only accept cash, so please
> have the exact amount ready.

### T&C 8. Envío y entrega — reemplaza la sección completa

**es:**

> Despachamos a todo el territorio nacional a través de empresas de transporte,
> y también puedes recoger tu pedido sin costo en nuestro punto de Medellín.
>
> El costo del envío se calcula con la ciudad de destino que nos indiques y con
> el peso y las dimensiones de tu pedido. Te lo mostramos antes de pagar, como un
> valor separado del precio de los productos. Si no hay empresa de transporte que
> cubra la dirección que indicaste, te lo decimos en ese momento y podrás recoger
> el pedido en nuestro punto.
>
> El plazo de entrega es de `[[PLAZO DE ENTREGA REAL]]` días calendario contados
> desde la confirmación del pago; a falta de un plazo pactado expresamente contigo
> antes de comprar, aplica el término legal de treinta (30) días calendario. La
> fecha estimada que muestra la empresa de transporte al momento de la compra es
> una estimación suya, no un plazo distinto del que acabamos de indicar.
>
> Cuando despachemos tu pedido te enviaremos la empresa de transporte y el número
> de guía, y podrás consultar el estado del envío desde el enlace de seguimiento
> que te llega por correo.
>
> Si no entregamos dentro del plazo pactado, puedes terminar el contrato y
> recuperar tu dinero, incluido el costo del envío que hayas pagado.

**en:**

> We ship nationwide through carrier companies, and you may also pick up your
> order at no cost at our Medellín location.
>
> The shipping cost is calculated using the destination city you give us and the
> weight and dimensions of your order. We show it to you before you pay, as an
> amount separate from the price of the products. If no carrier covers the address
> you provided, we will tell you at that moment and you may pick the order up at
> our location.
>
> The delivery term is `[[ACTUAL DELIVERY TIME]]` calendar days from payment
> confirmation; absent a term expressly agreed with you before purchase, the legal
> term of thirty (30) calendar days applies. The estimated date shown by the
> carrier at the time of purchase is its own estimate, not a term different from
> the one stated here.
>
> When we ship your order we will send you the carrier and the tracking number,
> and you will be able to check the shipment status from the tracking link sent to
> your email.
>
> If we do not deliver within the agreed term, you may terminate the contract and
> recover your money, including any shipping cost you paid.

### T&C 9. Derecho de retracto — reemplaza el cuarto párrafo

**es:**

> El costo del transporte de la devolución lo asumes tú, como lo establece el
> artículo 47 de la Ley 1480 de 2011. Lo que te reintegramos es todo lo que
> pagaste por la compra, incluido el costo del envío que te cobramos al comprar, y
> los gastos que genere esa devolución del dinero los asumimos nosotros.

**en:**

> You bear the cost of return shipping, as established by article 47 of Law 1480
> of 2011. What we refund is everything you paid for the purchase, including the
> shipping cost we charged you at checkout, and we bear any expenses generated by
> that refund.

### Política de datos 7. Finalidades — reemplaza el primer elemento de la lista

**es:**

> Procesar, confirmar y despachar tu pedido, cotizar el costo del envío con tu
> ciudad de destino, generar la guía de transporte y mantenerte informado del
> estado del envío.

**en:**

> Process, confirm and ship your order, quote the shipping cost using your
> destination city, generate the shipping label, and keep you informed of the
> shipment status.

### Política de datos 8. Encargados y terceros — reemplaza el elemento de la transportadora

**es:**

> Skydropx, como plataforma de logística y envíos, y las empresas de transporte
> que ella contrata para entregar tu pedido; reciben tu nombre, teléfono,
> dirección de entrega y ciudad, y el valor a recaudar cuando pagas contra
> entrega.

**en:**

> Skydropx, as our logistics and shipping platform, and the carrier companies it
> engages to deliver your order; they receive your name, phone number, delivery
> address and city, and the amount to be collected when you pay on delivery.

### Política de datos 9. Transferencia y transmisión internacional — reemplaza el párrafo

**es:**

> Nuestra infraestructura se apoya en servicios de computación en la nube que
> pueden almacenar o procesar información en servidores ubicados fuera de
> Colombia, y nuestra plataforma de logística y envíos es una empresa de origen
> mexicano. Esas transmisiones se hacen bajo contrato con cada proveedor, con el
> único fin de prestar el servicio descrito en esta política, y hacia países
> respecto de los cuales la Superintendencia de Industria y Comercio ha
> determinado que ofrecen un nivel adecuado de protección de datos personales.

**en:**

> Our infrastructure relies on cloud computing services that may store or process
> information on servers located outside Colombia, and our logistics and shipping
> platform is a company of Mexican origin. Those transmissions are made under
> contract with each provider, for the sole purpose of providing the service
> described in this policy, and to countries which the Superintendency of Industry
> and Commerce has determined offer an adequate level of personal data protection.

### Textos del checkout que hoy no existen

El deber de información se cumple **en la pantalla donde se paga**, no en una
subpágina legal. Estas claves hay que crearlas en
`apps/web/src/assets/i18n/scopes/checkout/{es,en}.json`, y la prueba de paridad
de claves ya existente (`core/i18n/claves-i18n.spec.ts`) cubre el scope
`checkout`, así que una clave sin traducir hace fallar la suite.

| Clave | es | en |
|---|---|---|
| `resumen.costo_envio` | Costo de envío | Shipping cost |
| `resumen.total` | Total a pagar | Total to pay |
| `resumen.envio_estimado` | Entrega estimada: {{dias}} días | Estimated delivery: {{dias}} days |
| `resumen.envio_calculando` | Calculando el costo de envío… | Calculating shipping cost… |
| `resumen.envio_sin_cobertura` | No tenemos transporte hasta esta dirección. Puedes recoger tu pedido en nuestro punto de Medellín. | We have no carrier for this address. You can pick up your order at our Medellín location. |
| `resumen.retiro_en_punto` | Recoger en el punto (Medellín) — sin costo de envío | Pick up at our location (Medellín) — no shipping cost |
| `resumen.retiro_ahorro` | Te ahorras {{valor}} de envío | You save {{valor}} in shipping |
| `metodoPago.contraentrega_efectivo` | Contra entrega: pagas el total, envío incluido, y solo en efectivo | Cash on delivery: you pay the total, shipping included, in cash only |

**Ojo con la lección de la Fase 6:** una clave traducida no es una clave visible.
`comun.traduccion_cortesia` existía, correcta, y ninguna plantilla la pintaba —
jurídicamente no estaba. Cada una de estas claves tiene que quedar **renderizada y
verificada en el navegador**, no solo escrita en el JSON.

---

## 4. Campos por completar

**Ninguno de estos marcadores se publica ya.** El 10 de septiembre de 2026 los
seis que estaban impresos en los documentos legales se cerraron con una de las dos
salidas del `ADR-0025` —quitar la promesa concreta, o declarar el mínimo legal
diciendo que es el legal—, y una guarda (`npm run marcadores`) impide que vuelva a
pasar. Lo que sigue abierto es el **dato**, no el texto: el documento publicado es
verdadero sin él.

| Marca | Qué falta | Quién lo decide | Qué dice el texto mientras tanto |
|---|---|---|---|
| `[[PLAZO DE ENTREGA REAL]]` | **Nada: se decidió no prometer plazo propio** (10 de septiembre de 2026). El sitio queda atado a los plazos de la transportadora y a la gestión de Skydropx, así que lo que obliga es el término legal | Decidido | El término legal supletivo de 30 días calendario, declarado como legal. En la Fase 7, el estimado de la cotización se muestra **como estimado** |
| `[[HORARIO DE ATENCIÓN]]` | **Nada: cerrado con dato** (todos los días, 8:00 a.m.–9:00 p.m.). Es horario de canales, no de local | Decidido | Publicado en los términos y en el pie. No se emite como `openingHours` |
| `[[PROVEEDOR DE CORREO TRANSACCIONAL]]` | **Nada: Resend también en producción** (`docs/07-infra-gcp.md`). La política de datos lo nombra | Decidido | Nombrado. Queda pendiente de contrato la región de procesamiento y la razón social |
| `[[TRANSPORTADORA]]` | Se nombra a Skydropx y a las transportadoras el día que reciban datos, que es un paso de esta fase | Ya decidido (`ADR-0021/0023`); falta construirlo | "La empresa de transporte que despache tu pedido" |
| Razón social exacta de Skydropx | Con qué entidad se contrata: la mexicana o una filial colombiana. Cambia el análisis de transferencia internacional | Negocio, al firmar |
| Límites y costos del recaudo | Mínimo, máximo, comisión, seguro obligatorio y plazo de dispersión. La ayuda pública reporta COP 2.000 y COP 2.000.000 | Contrato con Skydropx |
| IVA sobre el flete cobrado | Si el costo de envío que se le cobra al comprador lleva IVA | Contador |

`[[QUIÉN PAGA EL FLETE DE DEVOLUCIÓN]]` **queda resuelto** y sale de la lista: lo
paga el comprador, y no por decisión del negocio sino porque el art. 47 lo dice.
Conviene anotar cuánto tardó eso en llegar al documento: esta línea se escribió el
8 de septiembre y el marcador siguió publicado hasta el 10, porque resolver un
dato en un documento de trabajo no cierra el texto que lo promete. Y no era una
respuesta sino dos: en garantía el transporte lo paga el vendedor (art. 11).

`[[GARANTÍA DE CELULARES]]` **también sale**, y por el mismo motivo: no hay
régimen especial para equipos terminales. Un año para producto nuevo, y el mayor
que anuncie el productor si lo anuncia.

---

## 5. Auditoría: promesas rastreadas hasta el código

Estado del sistema **hoy**, antes de implementar `adr/0021`. Veredicto contra lo
que el documento publicado promete y contra lo que la ley exige.

| Promesa | Dónde se promete | Dónde se cumple | Veredicto |
|---|---|---|---|
| El precio incluye el envío y no hay cobros adicionales | `legales/es.json:186` | `Pedido.total()` suma solo líneas (`domain/pedido/Pedido.java:165`) | **Coincide hoy.** Deja de coincidir el día que entre la cotización |
| Retiro en punto "sin costo" | `checkout/es.json:13` | No hay costo de envío que evitar: ya está en el precio | **Coincide, pero engaña.** El comprador cree que ahorra y no ahorra nada |
| Resumen antes de pagar con costos de envío separados y total a pagar (art. 50) | Obligación legal; ningún documento la enuncia | El resumen muestra solo "Subtotal" (`checkout/presentation/resumen/resumen.page.html:55`) | **Hueco de la ley.** Hoy es inofensivo porque subtotal = total; con flete aparte, incumple |
| El costo de envío lo recalcula el backend | Regla dura #7 y `apps/web/CLAUDE.md` | No existe: no hay cotizador ni columna | **No existe todavía**, y hoy no hace falta |
| Plazo de entrega de 30 días calendario | `legales/es.json:212` | Nada lo mide ni lo alerta | **Se atiende a mano** |
| Retracto de 5 días hábiles desde la entrega | `legales/es.json:219` | `MarcarEntregado` fija la fecha solo si alguien pulsa el botón | **Se atiende a mano, y la fecha depende de un humano** |
| Reintegro en 15 días calendario | `legales/es.json:221` | Ningún flujo devuelve dinero | **Se atiende a mano** (arrastrado de la Fase 6) |
| Encargado: la transportadora recibe nombre, teléfono y dirección | `legales/es.json:92`, sin resolver | Ninguna integración logística existe todavía | **Coincide por accidente**: no hay transportadora conectada, y por eso el `[[ ]]` no ha hecho daño |
| El seguimiento no expone datos internos | Ninguna promesa escrita; es criterio de diseño | `MapeadorRespuestasPedido.aRespuestaPublica` (línea 59) devuelve el `Envio` completo | **No coincide.** Ver hallazgo 3 |

---

## 6. Hallazgos, por riesgo

### Nivel 1 — bloquean el encendido de la cotización

**1. El texto publicado quedará falso el día que se cobre el flete.**
*Incoherencia de texto, no bug.* `apps/web/src/assets/i18n/scopes/legales/es.json:186`
(y su par en `en.json`) dice que el precio incluye el envío y que "no hay cobros
adicionales al final del proceso". Un cobro adicional frente a un documento propio
que promete que no habrá ninguno es la prueba escrita en contra que la SIC lee sin
discutir. **Cierre:** las cláusulas de la sección 3, en el mismo commit, más
`legales.comun.version`, `legales.comun.vigencia` y `POLITICA_DATOS_VERSION`
actualizadas — las tres van acopladas al texto y se mueven juntas.

**2. El resumen del checkout no muestra ni el costo de envío ni el total a pagar.**
*Hueco de la ley (art. 50).* `apps/web/src/app/features/checkout/presentation/resumen/resumen.page.html:55`
pinta una sola línea, `checkout.resumen.subtotal`, y el valor lo calcula el
navegador (`resumen.page.ts:64`). Falta la línea de envío, falta el total, y falta
que las dos cifras vengan del servidor. **Cierre:** las claves de la sección 3, el
costo de envío devuelto por `POST /api/v1/envios/cotizacion` y el total recalculado
por `POST /api/v1/pedidos`. **Prueba que lo sostiene:** un pedido con flete cuyo
total mostrado es subtotal + envío, y un `costoEnvio` manipulado en el cuerpo de la
petición que el servidor ignora.

**3. El seguimiento público expone el costo real del flete y la comisión de
recaudo.** ~~*Bug, y existe hoy.*~~ **Cerrado el 9 de septiembre de 2026**, antes
de la Fase 7 y no dentro de ella: al hacer visible el retracto en esa misma
respuesta no tenía sentido añadirle campos a un DTO que ya filtraba de más. El
cierre fue el que este documento pedía —un DTO público reducido— y la lección de
fondo quedó escrita en `PedidoSeguimientoRespuesta`: el panel y el comprador
compartían el mismo record, y por eso la fuga era invisible. Dos audiencias, dos
tipos. La prueba afirma sobre el texto crudo de la respuesta que las palabras
`costoEnvio` y `comisionRecaudo` no aparecen.
`apps/api/presentation/.../pedido/MapeadorRespuestasPedido.java:59` construye la
respuesta pública anulando con cuidado el `actor` y el `motivo` del historial
—porque no son del comprador— y a continuación copia `completa.envio()` tal cual,
que es un `EnvioRespuesta` con `costoEnvio` (lo que la transportadora nos cobra) y
`comisionRecaudo` (`dto/EnvioRespuesta.java:11`). Cualquiera con un id de pedido y
el correo correcto ve el margen del negocio en ese envío. Con `adr/0021` empeora:
habría **dos** `costoEnvio` distintos en la misma respuesta —el que el comprador
pagó y el que el negocio pagó— y el comprador leería el que le convenga.
**Cierre:** un DTO público reducido a transportadora, guía, URL de rastreo, plazo
estimado y eventos. **Prueba:** que la respuesta de seguimiento no contenga la
palabra `comisionRecaudo` ni el costo real.

### Nivel 2 — se atienden a mano y alguien tiene que saberlo

**4. Retracto, garantía y reversión siguen sin flujo.** Arrastrado del cierre de la
Fase 6 y sin cambios. Lo que cambia es que ahora hay una cifra más que decidir a
mano en cada caso: si el reintegro incluye el flete de ida (sí, según la sección 3)
y quién paga el de vuelta (el comprador). Sin flujo, eso lo tiene que recordar una
persona en cada devolución.

**5. La fecha de entrega, de la que cuelgan dos plazos legales, depende de que
alguien pulse un botón.** `MarcarEntregado` solo corre desde el panel. `adr/0022`
lo cierra con el webhook más la conciliación programada, y por eso esa tarea
programada no es un lujo operativo: sin ella, un `delivered` perdido corre el
retracto y la garantía sin que el sistema los esté contando.

### Nivel 3 — incoherencias latentes

**6. "Retiro en punto (Medellín, sin costo)" hoy engaña.**
`apps/web/src/assets/i18n/scopes/checkout/es.json:13`. Con el flete embebido en el
precio, recoger no ahorra un peso, y el texto sugiere que sí. Es del tipo de
promesa que la ley lee a favor del consumidor. Con la cotización se vuelve cierta,
y entonces hay que mostrar **cuánto** se ahorra (`resumen.retiro_ahorro`).

**7. Dos plazos de entrega compitiendo.** El documento dirá `[[PLAZO DE ENTREGA
REAL]]`, con el supletivo de 30 días calendario; la cotización devolverá un
estimado de la transportadora, que el checkout quiere mostrar porque es útil. Si la
pantalla dice 3 días y el documento 30, el comprador leerá 3 y la ley lo respalda.
La cláusula redactada resuelve el conflicto declarando que el estimado es una
estimación del transportador y no un plazo pactado — pero eso solo funciona si la
pantalla lo dice con las mismas palabras. **Cierre:** `resumen.envio_estimado` dice
"estimada", nunca "garantizada".

**8. `Pedido.total()` documenta la premisa vieja en su propio Javadoc.**
`apps/api/domain/src/main/java/co/tecnosport/api/domain/pedido/Pedido.java:165`:
*"El envío no se agrega: ya está en cada precio unitario."* No es un hallazgo
legal, es el punto exacto donde empieza la implementación, y el comentario tiene
que morir con el cambio o se convierte en la próxima mentira documentada.

### Nivel 4 — deuda de evidencia

**9. Nada guarda qué cotización se le mostró al comprador.** Si alguien reclama que
se le cobró un flete distinto del que vio, la defensa es la tarifa congelada en el
pedido (`adr/0021`) — que por eso incluye el identificador del proveedor y el
vencimiento. Sin ese registro, la discusión es palabra contra palabra.

**10. El evento del proveedor y su recepción son fechas distintas y hay que
guardar las dos.** `evento_seguimiento.ocurrido_en` y `recibido_en`
(`docs/02-modelo-datos.md`). Un `delivered` del lunes recibido el jueves corre
plazos desde el lunes; la segunda fecha es la única que explica por qué nadie se
enteró.

---

### Lo que se revisó y salió limpio

Se anota porque un "coincide" verificado vale tanto como un hallazgo, y porque
así se sabe qué no hay que volver a mirar:

- **Ninguna insignia promete envío gratis.** Buscado en todo `apps/web` (i18n y
  plantillas): las únicas menciones al envío en la vitrina son el lema "Envío a
  todo el país desde Medellín" (`assets/i18n/es.json:50`) y la descripción de SEO,
  que no prometen precio. El cambio no arrastra publicidad de la portada.
- **La paridad de claves es/en está protegida en el scope `checkout`**
  (`core/i18n/claves-i18n.spec.ts:45`), así que una clave nueva sin traducir hace
  fallar la suite. Ojo: paridad **no** es renderizado, y lo renderizado es lo que
  cuenta.
- **La plantilla legal pinta `parrafos`, `lista` y `cierre`**, las tres
  estructuras que usan las cláusulas nuevas.
- **Cookies sin cambios.** Skydropx no carga nada en el navegador: la cotización y
  el seguimiento los llama el backend. La política de cookies no se toca y el
  argumento para no tener banner sigue en pie.

## 7. Puntos para revisión de abogado

1. **¿La reversión del pago (art. 51) alcanza el costo del envío?** El decreto que
   la reglamenta contempla la reversión parcial cuando la compra fue de varios
   productos, pero no encontramos texto oficial que resuelva expresamente qué pasa
   con el flete. La redacción propuesta no toca el numeral 11 y deja la pregunta
   abierta a propósito.
2. **¿Se puede pactar un plazo de entrega distinto del supletivo de 30 días
   mostrando el estimado del transportador en el checkout?** La norma pide
   aceptación expresa y previa del plazo pactado. Mostrar un estimado no parece
   equivalente a pactar, y de ahí la cláusula que los distingue. Confirmar si hace
   falta una aceptación explícita del plazo para poder oponerlo.
3. **La entidad con la que se contrata Skydropx y el régimen de la transferencia.**
   México está en la lista de nivel adecuado de la SIC, pero si el contrato es con
   una filial colombiana el análisis es otro, y si hay subencargados en otros
   países hay que mirarlos. Punto para abogado con el contrato firmado a la vista.
4. **Reintegro del flete de ida en el retracto.** La redacción propuesta lo
   reintegra, por la lectura estricta del art. 47: el consumidor solo asume los
   costos de la devolución. Es la posición favorable al consumidor y la más segura;
   confirmar si el negocio quiere sostener otra, sabiendo que la duda se interpreta
   a favor del consumidor.
5. **Cobrar contra entrega el flete además de la mercancía** cuando la
   transportadora suma su propia comisión de recaudo: confirmar que no se está
   trasladando al comprador un cargo que no se le informó como tal.

---

## 8. Lista de verificación antes de publicar

- [ ] Las cláusulas de la sección 3 pegadas en `es.json` **y** en `en.json`, con la
      prueba de paridad de claves en verde.
- [ ] `legales.comun.version` y `legales.comun.vigencia` actualizadas, y
      `POLITICA_DATOS_VERSION` con ellas — el `lastmod` del sitemap sale de ahí.
- [ ] Los `[[ ]]` de la sección 4 resueltos o conscientemente publicados como
      pendientes. Ninguno llega a producción con los corchetes puestos.
- [ ] Las claves nuevas del checkout **renderizadas y vistas en el navegador**, en
      los dos idiomas.
- [ ] El resumen del checkout muestra subtotal, costo de envío y total, con las
      cifras que devuelve el servidor.
- [ ] El seguimiento público ya no expone costo real ni comisión de recaudo.
- [ ] `axe` en verde en las tres páginas legales y en el checkout, que es donde
      alguien va a leer esto justo cuando tiene un problema.
- [ ] Un abogado colegiado revisó los cinco puntos de la sección 7.
