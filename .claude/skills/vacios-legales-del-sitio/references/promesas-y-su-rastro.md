# Promesas y su rastro en el sistema

Para cada promesa típica de una tienda en línea colombiana: qué tiene que existir
en el código para que sea verdad, y cuál es el eslabón que suele faltar.

El detalle normativo —artículos, plazos y su vigencia— vive en
`references/marco-normativo.md` de la skill `textos-legales-comerciales`. Aquí
solo está el rastro técnico. **Verifica el plazo allí antes de declarar que el
del sistema está mal.**

## Cómo se lee cada ficha

- **Promete** — la frase del documento que hay que rastrear.
- **Rastro mínimo** — lo que tiene que existir. Si falta un eslabón, hay hueco.
- **Dónde suele romperse** — el fallo concreto que aparece una y otra vez.
- **Prueba que lo sostiene** — qué comprobar para que no se reabra.

---

## Derecho de retracto

**Promete:** que el comprador puede arrepentirse dentro del plazo legal desde la
entrega, sin justificar, y recuperar su dinero en el plazo legal.

**Rastro mínimo:**

1. Una forma de solicitarlo que no dependa de escribir un correo a ciegas: una
   pantalla en el detalle del pedido, o un canal anunciado y atendido.
2. Un **estado en el grafo del pedido** que represente "retracto solicitado" y
   otro para su resolución. Si el enum de estados no los tiene, el flujo no
   existe, por mucho que el texto lo prometa.
3. El **cálculo del plazo desde la fecha de entrega**, no desde la compra ni
   desde el pago. Son fechas distintas y la que manda es la entrega.
4. Días **hábiles** para el retracto y días **calendario** para el reintegro: son
   dos unidades distintas en la misma cláusula, y confundirlas cambia el
   resultado en días.
5. Un movimiento de dinero real hacia el mismo instrumento de pago, o el medio
   acordado, con su registro.
6. Efecto en inventario: lo devuelto vuelve a existir, o no vuelve, pero la
   decisión está tomada en el código y no la toma el azar.

**Dónde suele romperse:** el eslabón 5. Hay estado, hay pantalla, hay correo — y
el dinero lo devuelve una persona a mano desde el panel de la pasarela, sin que
el sistema lo sepa. El pedido queda "reembolsado" sin que exista prueba de que
alguien reembolsó.

**Prueba que lo sostiene:** un pedido entregado dentro del plazo admite retracto y
uno fuera del plazo no, con el límite exacto probado por los dos lados. Y que el
plazo salga de configuración, no de un número escrito en el caso de uso.

---

## Reversión del pago

**Promete:** que ante fraude, no entrega, producto distinto al pedido o producto
defectuoso, el comprador puede pedir que se revierta el pago, y que el comercio
facilita el trámite.

**No es lo mismo que el retracto** y confundirlos en el informe es un error de
fondo: el retracto no necesita motivo y lo resuelve el comercio; la reversión
tiene causales tasadas e involucra al emisor del medio de pago.

**Rastro mínimo:**

1. Un canal para solicitarla, y que el texto diga que también debe pedirse ante
   el emisor del medio de pago.
2. Registro de la solicitud con su causal, porque las causales son distintas y la
   respuesta también.
3. Interacción real con la pasarela para la reversión, o el proceso manual
   documentado con su responsable.
4. Trazabilidad de la fecha de solicitud, que es la que corre.

**Dónde suele romperse:** el documento la menciona porque la ley la exige, y el
sistema no distingue una reversión de una devolución cualquiera. Todo termina en
el mismo estado genérico y las causales se pierden.

**Prueba que lo sostiene:** que la causal quede guardada y que un pedido pueda
llegar a reversión sin pasar por retracto.

---

## Garantía legal

**Promete:** que el producto funciona y sirve para lo ofrecido, y que durante el
término de garantía hay reparación, reposición o devolución del dinero.

**Rastro mínimo:**

1. La fecha de entrega guardada por línea o por pedido: el término corre desde
   ahí.
2. El término aplicable, que no es uno solo — depende de si el producto es nuevo,
   usado o perecedero, y de lo que anuncie el productor. Un único número
   incrustado para todo el catálogo es un hallazgo.
3. Un canal de solicitud y un registro de cada reclamación con su fecha.
4. Las tres salidas posibles representadas, no solo la devolución del dinero.

**Dónde suele romperse:** el término se trata como una constante global cuando el
catálogo mezcla categorías con términos distintos. Y la reclamación se atiende por
correo sin dejar registro, con lo cual no hay forma de probar que se respondió en
plazo.

**Prueba que lo sostiene:** que el término se calcule desde la entrega y que
productos de categorías con términos distintos den resultados distintos.

---

## Plazo de entrega

**Promete:** entregar en el plazo anunciado, y si no hay disponibilidad,
informarlo.

**Rastro mínimo:**

1. El plazo anunciado, en configuración y no repetido en tres sitios.
2. Que el plazo mostrado en el checkout sea **el mismo** que el del documento
   legal. Dos números distintos en dos pantallas es incumplimiento del deber de
   información aunque los dos sean razonables.
3. Si se pacta un plazo distinto del término general, evidencia de la
   **aceptación expresa y previa** del comprador. Sin ese registro, el plazo
   pactado no se puede oponer.
4. Un camino para el incumplimiento: terminar el contrato y devolver el dinero.

**Dónde suele romperse:** el plazo aparece como texto suelto en una plantilla de
i18n y como constante en el backend, y nadie los sincroniza. O el documento trae
el término supletivo legal porque el plazo real nunca se decidió — eso no es un
bug, es un dato de negocio pendiente y hay que marcarlo `[[ ]]`.

---

## Derechos del titular sobre sus datos

**Promete:** conocer, actualizar, rectificar y suprimir sus datos, revocar la
autorización, pedir prueba de la autorización y ser informado del uso.

**Rastro mínimo, derecho por derecho:**

| Derecho | Qué exige del sistema |
|---|---|
| Conocer / acceder | Poder entregar al titular lo que hay sobre él, gratis |
| Actualizar / rectificar | Pantalla de cuenta que permita corregir, o canal atendido |
| Suprimir | Un borrado real, y una decisión escrita sobre qué **no** se borra por obligación tributaria o contable |
| Revocar la autorización | Un registro de la revocación y su efecto sobre los envíos comerciales |
| Prueba de la autorización | La constancia recuperable: quién, cuándo, qué versión del texto, desde dónde |
| Ser informado del uso | Que las finalidades declaradas coincidan con las reales |

**El más delicado es la supresión.** "Borramos tus datos" y una obligación de
conservar facturación durante años son dos cosas verdaderas a la vez, y el
documento tiene que decir cuál gana. Si el texto promete borrado total y el
sistema conserva la factura, hay incoherencia; si el sistema borra la factura,
hay un problema mayor.

**Dónde suele romperse:** existe la constancia de la autorización pero no hay
forma de recuperarla por titular; y no existe ningún flujo de supresión, con lo
cual el derecho se ejerce por correo, a mano, sin plazo controlado.

**Prueba que lo sostiene:** que la constancia se pueda recuperar por correo del
titular, y que la supresión deje lo que debe quedar y borre lo que debe irse.

---

## Autorización de tratamiento de datos

**Promete:** que el tratamiento se hace con autorización previa, expresa e
informada.

**Rastro mínimo:**

1. Casilla **separada** de la aceptación de términos y **nunca premarcada**.
2. La **versión del texto la fija el servidor**, jamás el cliente. Si el
   navegador declara qué versión aceptó, basta manipular la petición para dejar
   constancia de una aceptación que no ocurrió.
3. Constancia persistida con correo, versión, momento y origen.
4. La versión del texto y la fecha de vigencia **acopladas al texto mismo**:
   cambiar uno sin el otro rompe la trazabilidad.
5. Que la guarda de autorización se evalúe **antes** que cualquier otra
   validación que revele información o consuma recursos. Comprobar primero si el
   correo está duplicado le confirma a quien no consintió nada que ese correo
   tiene cuenta.

**Dónde suele romperse:** el punto 5, y el 2 en sistemas que confían en el
cliente.

---

## Canales de atención, PQR y horario

**Promete:** un correo, un teléfono, un WhatsApp, un formulario, un horario.

**Rastro mínimo:** que existan, que alguien los lea, y que el que aparece en el
documento sea el mismo que aparece en el pie, en el checkout y en los correos
transaccionales. Tres direcciones distintas en tres lugares es un hallazgo.

**Dónde suele romperse:** el horario de atención es casi siempre un dato de
negocio que nadie decidió. No se inventa: `[[ ]]`.

---

## Terceros que tratan datos

**Promete:** una lista de con quién se comparten los datos.

**Rastro mínimo:** que la lista del documento coincida con los terceros que de
verdad reciben datos. Se rastrea por integraciones reales, no por memoria:
pasarela de pago, transportadora, proveedor de correo transaccional, nube donde
corren la aplicación y la base, almacenamiento de archivos, analítica, chat de
soporte, y cualquier servicio al que se envíe un correo o una dirección.

**Dónde suele romperse:** el proveedor de correo transaccional y el
almacenamiento de imágenes se olvidan siempre. Y si hay transferencia
internacional, hay un régimen propio que declarar.

---

## Precio, impuestos y total

**Promete:** que el precio anunciado es el que se cobra.

**Rastro mínimo:** que el total mostrado antes de pagar incluya todo lo que se va
a cobrar, y que **el servidor lo recalcule** antes de cobrar. Un precio que viaja
desde el navegador no es un precio, es una sugerencia.

**Dónde suele romperse:** el costo de envío y los impuestos se calculan en dos
sitios con dos redondeos.

---

## Cookies y rastreo

**Promete:** qué cookies usa el sitio y para qué.

**Rastro mínimo:** que la lista coincida con lo que el navegador de verdad
guarda. Se comprueba abriendo el sitio y mirando, no leyendo el código.

**Dónde suele romperse:** al revés de lo esperado — el documento describe un
banner y una analítica que el sitio no tiene, porque el texto salió de una
plantilla. Prometer menos rastreo del que se hace es grave; declarar rastreo que
no existe solo es falso, pero también incumple el deber de información veraz.

---

## Identificación del vendedor

**Promete, o debe prometer:** nombre o razón social, identificación tributaria,
dirección física en Colombia, teléfono y correo, consultables antes de comprar.

**Rastro mínimo:** que estén visibles sin entrar a una subpágina, y que el
**dígito de verificación del NIT esté bien calculado**. Este proyecto ya tuvo un
NIT con el dígito equivocado durante meses en el pie del sitio; se calcula con el
algoritmo de la DIAN, no se elige.

**Dónde suele romperse:** los datos existen en el pie y no en el checkout, o el
NIT se transcribió mal y nadie lo verificó nunca.
