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

## Antes de las fichas: cuenta los caminos que terminan en devolver dinero

Las fichas que siguen están separadas por figura legal, y leerlas una por una
tiene un efecto secundario que hay que neutralizar de entrada: **se encuentran
tres huecos donde hay uno solo, y se pierden los que no tienen ficha.**

Haz este inventario **antes** de rastrear ninguna figura. Recorre los documentos
publicados buscando toda frase que prometa devolver dinero, tenga o no un nombre
legal, y anota su disparador y su plazo. En una tienda colombiana típica salen
más de los tres que uno espera:

| Camino | Lo dispara | Suele tener ficha propia |
|---|---|---|
| Retracto | el comprador se arrepiente, sin motivo | sí |
| Reversión del pago | fraude, no entrega, producto distinto o defectuoso | sí |
| Garantía legal | el producto falla, y la salida elegida es devolver el dinero | sí |
| **No disponibilidad sobrevenida** | el stock desaparece después de comprar | **no** |
| **Incumplimiento del plazo de entrega** | no se entregó a tiempo y el comprador termina el contrato | **no** |
| Rechazo o cancelación antes de entregar | según cómo esté redactado | no |

Los dos marcados son los que se escapan, y se escapan por la misma razón: viven
en secciones del documento que no se leen como secciones de dinero —
"Disponibilidad", "Envío y entrega"— y aun así traen un plazo y una obligación de
reintegrar.

**Qué hacer con el inventario, que es el punto:**

- **Cada camino conserva su propio disparador, su propio plazo y su propia
  causal.** No se unifican: son obligaciones distintas y confundirlas es el error
  de fondo que la ficha de reversión ya advierte.
- **La constancia del dinero que salió es una sola cosa** —cuánto, por qué medio,
  cuándo, quién lo registró y con qué comprobante— y debería modelarse una vez.
  Un sistema con tres constancias distintas para el mismo hecho no puede
  responder "cuánto dinero devolvimos el mes pasado" sin sumar a mano.
- **Si en el sistema ya existe uno de estos caminos, ábrelo y mira dónde vive su
  constancia.** El primero que se construye casi siempre la mete *dentro* de su
  propio agregado, con un argumento correcto —"así es imposible un reembolso sin
  solicitud"—, y esa decisión, que era buena para uno, es la que bloquea a los
  otros cuatro. Encontrarla ahora cuesta una nota en el informe; encontrarla
  después cuesta una migración.

**Prueba que lo sostiene:** que el inventario de caminos esté escrito y fechado.
Es lo único que hace visible el camino que nadie contó.

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

**Promete:** un correo, un teléfono, un WhatsApp, un formulario, un horario, y
—cuando el texto usa la palabra **radicación**— que la solicitud queda radicada,
que es bastante más que leída.

**Rastro mínimo:**

1. Que los canales existan y que el que aparece en el documento sea el mismo que
   aparece en el pie, en el checkout y en los correos transaccionales. Tres
   direcciones distintas en tres lugares es un hallazgo.
2. Un **registro por solicitud** con su identificador visible para quien la
   presentó. "Radicar" sin número de radicado es recibir, no radicar; y sin
   identificador el comprador no tiene cómo referirse después a lo que pidió.
3. **Dos fechas, no una:** cuándo llegó la solicitud y cuándo se registró. El
   plazo corre desde la primera; la segunda es la única que explica por qué nadie
   se enteró. Cuando el canal es un correo o un WhatsApp, quien radica es una
   persona del negocio, y el sistema deja constancia de un acto que ocurrió por
   fuera: guarda **quién radicó**, y esa constancia no sustituye el acto.
4. **El tipo de la solicitud, porque de él depende el reloj.** Este es el punto
   que se salta todo el mundo: el mismo buzón recibe peticiones que la ley cuenta
   con relojes distintos. Consultas y reclamos de datos personales tienen sus
   plazos y sus prórrogas; las peticiones del consumidor, los suyos; y el sitio
   además suele **prometer** un plazo propio. Verifica cada uno en
   `marco-normativo.md`: no los unifiques en una constante.
5. La **respuesta** registrada con su fecha, y la **prórroga** —si el plazo la
   admite— registrada como tal, con el aviso al interesado antes de que venza el
   plazo inicial. Una prórroga que nadie avisó no es una prórroga.
6. Alguna forma de **ver lo que está por vencerse**. Un plazo que solo existe en
   una columna no lo cumple nadie.

**Dónde suele romperse:** dos sitios.

- El horario de atención es casi siempre un dato de negocio que nadie decidió. No
  se inventa: `[[ ]]`.
- **Los documentos se contradicen entre sí sobre el mismo buzón.** Los términos
  prometen un plazo para "toda petición" y la política de datos promete otro,
  más corto, para las consultas — y las dos frases apuntan al mismo correo. No es
  una errata: es el caso 2 del encabezado de esta skill, el que nadie ve porque
  las dos partes funcionan. Compara los plazos **entre documentos**, no solo
  contra el código.

**Prueba que lo sostiene:** que una solicitud de cada tipo reciba el plazo que le
corresponde y no el mismo para todas; y que el vencimiento se calcule sobre la
fecha de llegada, no sobre la de registro.

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

## Costo de envío, cargos adicionales y recogida sin costo

**Promete, o debe prometer:** que antes de terminar la compra el comprador vea el
precio de cada producto, el subtotal, **el costo del envío como una cifra aparte**
y el total que va a pagar. En Colombia esto no depende de haberlo escrito: es una
obligación del régimen de comercio electrónico, que además pide que los costos
adicionales al precio se informen con su razón y su valor. **Verifica el artículo
en `marco-normativo.md` antes de citarlo.**

Es la ficha que hay que abrir siempre que el negocio cobre el flete aparte del
precio, cambie de un modelo a otro, o anuncie envío gratis.

**Rastro mínimo:**

1. **Una cifra, no una advertencia.** "Más gastos de envío" o "el envío se cobra
   aparte" no informan nada. Si en la pantalla donde se paga no hay un número,
   hay hueco.
2. **Una línea de envío y una de total, distintas del subtotal.** El caso que se
   escapa: una pantalla que muestra solo "Subtotal" y nada más. Cuando el envío
   va incluido en el precio, subtotal y total coinciden y nadie lo nota; el día
   que se cobre flete, esa misma pantalla incumple sin que nadie la haya tocado.
3. **El cálculo en el servidor.** El costo de envío es tan manipulable como el
   precio, y llega en el mismo cuerpo. La prueba es mandar un flete alterado y
   comprobar que el servidor lo ignora.
4. **La cotización mostrada, congelada en el pedido.** Con transportadora, valor,
   plazo y vencimiento. Sin ese registro, "me cobraron un flete distinto del que
   vi" es palabra contra palabra.
5. **Ningún cargo que aparezca después de aceptar el total.** Un flete
   recalculado al despachar, una comisión de recaudo trasladada al comprador o un
   ajuste por peso real son cobros no autorizados si no estaban en el resumen.
6. **Si hay una opción sin costo —recoger en el punto, retiro en tienda— el total
   de ese pedido es el subtotal exacto**, y el ahorro se muestra. Es publicidad y
   obliga.

**Dónde suele romperse:** el eslabón 2, y de la forma más silenciosa posible. Y
el 6 al revés de lo esperado: un "sin costo" que era falso mientras el flete
estaba embebido en el precio —recoger no ahorraba nada— y que nadie revisó al
cambiar el modelo.

**Prueba que lo sostiene:** un pedido con flete cuyo total es subtotal más envío;
el mismo pedido con retiro en punto y flete en cero; y un `costoEnvio` manipulado
en la petición que no cambia el total.

---

## Plazo de entrega estimado frente a plazo prometido

**Promete:** dos cosas que el comprador lee como una sola. El documento legal
promete un plazo; la pantalla muestra el estimado que devuelve la transportadora
al cotizar.

**Rastro mínimo:**

1. Que la pantalla diga **estimado** y el documento diga **pactado**, con
   palabras distintas y visibles, o que sean el mismo número.
2. Que el plazo del documento no sea el término supletivo legal mientras la
   pantalla anuncia tres días: el comprador leerá tres, y la ley lo respalda.
3. Que exista el camino del incumplimiento —terminar el contrato y devolver el
   dinero, **incluido el flete pagado**—, no solo la promesa.

**Dónde suele romperse:** nadie decide el plazo real, el documento se queda con
el supletivo legal, y la interfaz empieza a mostrar el estimado del proveedor
porque es útil. Quedan dos plazos compitiendo y ninguno de los dos es el que
alguien decidió.

---

## Fecha de entrega, seguimiento y los plazos que cuelgan de ella

**Promete:** poder consultar dónde va el pedido, y —sin decirlo— que los plazos
que la ley cuenta desde la entrega se cuenten de verdad.

Esta ficha existe porque la fecha de entrega **no es un dato operativo**: es el
disparador del retracto y de la garantía. Un sistema donde esa fecha depende de
que alguien pulse un botón tiene dos plazos legales colgando de la memoria de una
persona.

**Rastro mínimo:**

1. La fecha de entrega **registrada**, con su origen: la marcó un humano, la
   reportó la transportadora, la deduce una tarea programada.
2. **Dos fechas por evento del proveedor: cuándo ocurrió y cuándo se recibió.**
   Los plazos corren desde la primera; la segunda es la única que explica por qué
   nadie se enteró.
3. **Idempotencia y firma verificada** en el webhook que trae esos eventos. Un
   evento falsificado que marque un pedido como entregado adelanta plazos, y si
   hay recaudo, mueve dinero.
4. **Una red de seguridad que no dependa del webhook.** Los webhooks se pierden;
   un `entregado` perdido corre el retracto sin que el sistema lo cuente. Una
   consulta programada del estado real es lo que lo cierra.
5. **Qué ve el comprador y qué no.** El seguimiento suele ser el endpoint más
   laxo del sistema —público, con el correo o un token del correo— y muchas veces
   devuelve el agregado de envío completo. Ahí viajan el costo real del flete y
   la comisión de la transportadora, que son el margen del negocio, no datos del
   comprador. **Ábrelo y mira el DTO, no confíes en que alguien lo filtró.**

**Dónde suele romperse:** el 4 y el 5. El 5 es especialmente traicionero cuando
alguien ya tuvo el cuidado de anular unos campos sensibles del historial en la
misma función y copió el resto tal cual.

**Prueba que lo sostiene:** que un evento repetido no transicione dos veces; que
solo los estados que deben mover el pedido lo muevan; y que la respuesta pública
de seguimiento no contenga los campos de costo interno.

---

## Logística tercerizada

**Promete:** normalmente nada, y ahí está el problema. Una plataforma logística
que cotiza, rotula, entrega, recauda y notifica es varios terceros a la vez.

**Rastro mínimo:**

1. **Declarada como encargado**, con qué datos recibe: nombre, teléfono,
   dirección, ciudad, y el valor a recaudar si hay contraentrega.
2. **Las transportadoras que ejecutan la entrega también reciben esos datos.** Si
   el documento solo nombra al agregador, nombra a la mitad de la cadena.
3. **Régimen de transferencia internacional** si la plataforma no es del país. La
   región del despliegue y el país de la entidad con la que se firma son hechos
   verificables, no suposiciones. **Punto para revisión de abogado.**
4. **Las notificaciones del proveedor son un tratamiento aparte.** Si la
   plataforma le escribe al comprador por WhatsApp o por correo, alguien
   autorizó ese canal, o nadie lo hizo. Si se apaga, se apaga a propósito y queda
   escrito.
5. **El recaudo contra entrega tiene condiciones que son información al
   consumidor:** solo efectivo, montos mínimos y máximos, valor exacto a pagar.
   Van en el checkout, antes de elegir el método — no en el correo de
   confirmación, cuando ya no se puede cambiar de opinión.

**Dónde suele romperse:** el 2 y el 4. Y el 5, que se descubre el día que un
comprador no tiene efectivo en la puerta.

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
