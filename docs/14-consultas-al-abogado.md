# Consultas al abogado

Expediente interno. **No es asesoría jurídica y no se publica**: es la lista de
decisiones que este proyecto no puede tomar solo, preparadas para que un abogado
colegiado las resuelva en una hora en vez de leerse el sitio entero.

`docs/09-plan-de-arranque.md` las venía arrastrando desde el 10 de septiembre de
2026 como tres preguntas sueltas dentro de una entrada. Así no se le entregan a
nadie. Cada una lleva aquí lo mismo: **qué dice hoy el texto publicado, qué hace
el sistema, qué dice la norma verificada, qué recomienda este proyecto, y qué
cambia en el código según cada respuesta.** Lo último se añadió el 23 de
septiembre de 2026 y es lo que separa una respuesta de un commit: sin eso, lo que
vuelve del abogado es una nota que alguien tendrá que interpretar tres semanas
después.

### Esta es la lista entera, y no lo era

**El 23 de septiembre de 2026 había puntos para abogado en tres documentos**, y
el expediente que iba a mandarse era el de uno. `docs/12` §7 tenía cinco más,
sobre cláusulas **ya publicadas**, y `docs/08` tenía dos que no estaban aquí.
Pagar la hora con una de las tres listas deja las otras dos abiertas.

Se triaron todos, y el resultado fue que **la mitad no eran preguntas para un
abogado**:

| De dónde | Qué pasó |
|---|---|
| `docs/08`, registro ante la SIC | **Retirado.** El Decreto 090 de 2018 lo contesta: las personas naturales no están obligadas al RNBD |
| `docs/08`, la contradicción de plazos | **Cerrado con el texto.** Los arts. 14 y 15 de la Ley 1581 fijan los plazos; sobraba el "toda petición" de los términos |
| `docs/08`, Sistecrédito en la política | **Cerrado con el texto.** Lo que quedaba de derecho es el punto 6 de aquí |
| `docs/12` §7.2, pactar el plazo de entrega | **Retirado.** El texto publicado ya tomó la salida segura |
| `docs/12` §7.4, el flete de ida | **Mudado al dueño.** El análisis jurídico ya está hecho; queda una decisión de riesgo del negocio |
| `docs/12` §7.5, la comisión de recaudo | **Retirado.** La premisa de hecho es falsa, medida |
| `docs/12` §7.1 y §7.3 | **Siguen abiertos**, y van con este expediente |

Lo que queda para el abogado son **seis puntos aquí y dos en `docs/12` §7**. El
procedimiento está en la Fase 5c de la skill `vacios-legales-del-sitio`, que
nació ese día porque la skill abría puntos y no tenía con qué volver a mirarlos.

Las normas se verificaron contra el texto vigente en el SISJUR de la Alcaldía de
Bogotá el 19 de septiembre de 2026, no de memoria, y la lista de países con nivel
adecuado de protección contra la compilación oficial de la Circular 5 de 2017 de
la SIC. Donde no hay cita es porque no se encontró norma que lo resuelva, y eso
también es un dato. Donde la fuente no se dejó leer —el Título V consolidado es un
PDF escaneado— queda dicho en el punto que lo necesita, en vez de citarlo como si
se hubiera leído.

**Lo verificado el 23 de septiembre de 2026**, para el triaje de arriba: los
arts. 14 y 15 de la Ley 1581 —los plazos de consulta y reclamo— en el SISJUR de
la Alcaldía de Bogotá, y el Decreto 090 de 2018 —quién está obligado al RNBD— en
la SIC, que es la autoridad del registro.

---

## 1. "Desgaste normal" como exclusión de la garantía

> **Resuelto en parte el 19 de septiembre de 2026.** La exclusión se quitó del
> texto publicado y la lista se alineó con el art. 16. Lo que sigue abierto es la
> reformulación en positivo, que es donde hace falta criterio profesional.

**Qué decía.** `legales.terminos.secciones[9]` —el numeral **10** del documento;
aquí decía `[10]`, que es la reversión del pago—, en los dos idiomas:

> La garantía no cubre el daño causado por uso indebido, modificación no
> autorizada, desgaste normal ni fuerza mayor.

**Qué dice ahora:**

> La garantía no cubre el daño que provenga de fuerza mayor o caso fortuito, del
> hecho de un tercero, del uso indebido del producto —incluida la modificación no
> autorizada— o del incumplimiento de las instrucciones de instalación, uso o
> mantenimiento cuando el producto se entregó con manual en castellano. Son las
> causales del artículo 16 de la Ley 1480 de 2011, y demostrar que el daño viene
> de una de ellas nos corresponde a nosotros, no a ti.

**Qué hace el sistema.** Nada automático, y eso se comprobó antes de tocar el
texto: no hay ningún enum de motivos de rechazo ni ninguna regla que mencione el
desgaste. `DesenlaceGarantia` solo conoce las tres salidas que concede la ley
—reparar, reponer, reintegrar— y una reclamación negada se responde por el flujo
de atención, con texto libre. O sea que **esta frase no es decorativa: es el
único criterio escrito con el que una persona va a rechazar una reclamación
real**, y cambiarla cambia de verdad lo que pasa.

**Qué dice la norma, verificada.** El art. 16 de la Ley 1480 de 2011 enumera las
causales de exoneración y son cuatro: fuerza mayor o caso fortuito, hecho de un
tercero, uso indebido del bien por el consumidor, e incumplimiento de las
instrucciones de instalación, uso o mantenimiento —esta última **solo si se
entregó manual en castellano**—. "Desgaste normal" no aparece, ni ahí ni en los
arts. 7 y 8. Y el mismo artículo pone la carga de la prueba del lado del
obligado: le toca demostrar el nexo causal entre la causal que alega y el
defecto.

Y hay un agravante de método: la Ley 1480 es de orden público y se interpreta a
favor del consumidor. Una exclusión más amplia que la legal no solo es ineficaz
—no se puede oponer—, sino que en un expediente de la SIC se lee como cláusula
abusiva, lo que empeora la posición del negocio en toda la disputa, no solo en
ese punto.

### Lo que el cotejo destapó y no era la pregunta

La lista vieja no solo sobraba por un lado: **faltaba por dos**. Enumeraba
"fuerza mayor" pero no el caso fortuito, y no mencionaba el hecho de un tercero,
que son dos de las cuatro causales que la ley sí concede. O sea que el texto
renunciaba a defensas legítimas mientras se inventaba una que no existe. Las dos
cosas se arreglaron a la vez porque son la misma frase.

Se añadieron además dos precisiones que están en el artículo y no estaban en el
texto: que la causal del manual **solo opera si el manual se entregó en
castellano**, y que **la carga de la prueba es nuestra**. Ninguna de las dos es
una concesión: las dos estaban ya en la ley y callarlas solo servía para que
quien lee creyera otra cosa.

### La tensión que queda, y es la que decide el abogado

El desgaste normal sí importa comercialmente: una batería que pierde capacidad
con los ciclos, o unos tenis con la suela gastada al año, no son un defecto de
calidad ni de idoneidad. Quitar la palabra no vuelve falso ese hecho — lo deja
sin decir.

La pregunta no es si se puede excluir, que ya está contestada: no, con ese
nombre. Es **cómo se dice lo que sí es cierto**, y hay dos salidas:

1. **Reformular en positivo** dentro de la definición de qué cubre la garantía:
   que el deterioro esperable por el uso normal durante la vida útil del producto
   no constituye un defecto de calidad o idoneidad. Informa mejor, y arriesga que
   se lea como la misma exclusión con otro nombre.
2. **Callar** y resolver caso por caso, apoyándose en que la garantía cubre
   defectos y el desgaste no lo es. Más seguro, menos informativo, y deja a quien
   atiende el PQR sin criterio escrito.

Hoy el texto está en la opción 2, porque es la que no requiere criterio
profesional para sostenerse.

**Qué cambia según la respuesta:** la opción 1 añade un párrafo a
`legales.terminos.secciones[9]` en `es.json` y en `en.json`, sube la versión y la
vigencia del documento en sus cuatro copias —las dos de i18n, `.env.example` y
`application.yml`, que es lo que comprueba `npm run datos-negocio`— y no toca una
línea de código: no hay enum de motivos de rechazo que ajustar. La opción 2 no
cambia nada.

**Lo que decide el abogado:** cuál de las dos, y si elige la primera, con qué
redacción exacta. La decisión no es de estilo: la frontera entre "informar que el
desgaste no es un defecto" y "excluir el desgaste" es justo donde se juega si la
cláusula es abusiva.

**Y una tercera, que el cotejo dejó planteada:** si conviene enumerar las cuatro
causales de la ley —que es lo que se hizo— o si es preferible no enumerarlas y
remitirse al artículo. Enumerar informa mejor, pero cada enumeración que se
desvía del texto legal es una oportunidad de desviarse. Esta se pegó al artículo
a propósito.

---

## 2. Nombrar o no a las transportadoras que subcontrata Skydropx

**Dónde está.** `legales.privacidad.secciones[7]` —el numeral **8**—, la lista de a quién se le
comunican los datos:

> Skydropx S.A.S., sociedad colombiana con NIT 901.508.804-5 y domicilio en
> Bogotá D.C., como plataforma de logística y envíos, y **las empresas de
> transporte que ella contrata** para entregar tu pedido; reciben tu nombre,
> teléfono, dirección de entrega y ciudad, y el valor a recaudar cuando pagas
> contra entrega.

Skydropx está nombrada con NIT. Las transportadoras, no: van descritas por su
categoría.

**Qué hace el sistema.** Hoy son cinco y las conocemos por nombre —Servientrega,
Coordinadora, Envía, Inter Rapidísimo y 99 minutes— porque aparecen tarifa por
tarifa en cada cotización (`docs/13`). **Pero cuál de ellas recibe los datos de
un pedido concreto lo decide Skydropx al emitir la guía, no nosotros**, y el
conjunto cambia cuando la plataforma agrega o retira un conector: en lo que va de
esta integración cambió dos veces.

**Qué dice la norma.** Menos de lo que el encargo suponía, y conviene decirlo
porque cambia la pregunta:

- El **art. 12 de la Ley 1581 de 2012** enumera lo que hay que informarle al
  titular al pedir la autorización: el tratamiento y su finalidad, el carácter
  facultativo de las respuestas sobre datos sensibles, sus derechos, y la
  identificación del **Responsable**. No exige identificar a los terceros.
- El **art. 13 de la Ley 1581** dice a quién se le puede suministrar la
  información —titulares, entidades públicas en ejercicio de funciones legales, y
  terceros autorizados por el titular o por la ley— sin exigir que se nombren.
- El **art. 13 del Decreto 1377 de 2013** lista lo que debe contener la política
  de tratamiento: razón social y datos de contacto del responsable, el
  tratamiento y su finalidad, los derechos del titular, el área que atiende
  peticiones, los procedimientos para ejercerlos, y la fecha de vigencia. **No
  exige enumerar destinatarios.**

O sea: **nombrar a Skydropx con NIT ya es más de lo que la norma pide.** La
descripción por categoría de las transportadoras cumple el mínimo legal.

**La tensión real.** El mínimo legal no es el único criterio. El principio de
transparencia del art. 4 de la Ley 1581 y la práctica de la SIC empujan hacia
decir más, no menos, y una lista de cinco nombres es información útil para quien
va a recibir un paquete en su casa. Contra eso: una lista nombrada que quede
desactualizada es *peor* que una descripción correcta, porque pasa de ser
genérica a ser **falsa**, y mantenerla al día depende de un tercero que no nos
avisa.

**Recomendación.** Mantener la descripción por categoría como el texto que
obliga, y añadir una frase que diga que la lista vigente de transportadoras se
puede consultar escribiendo al correo de atención. Así la información existe y es
exigible sin convertir el documento en algo que caduca solo.

**Qué cambia según la respuesta:** las dos salidas tocan la misma viñeta de
`legales.privacidad.secciones[7]` en los dos idiomas, con su subida de versión. La
diferencia está después: la lista nombrada crea una tarea recurrente —cotejarla
contra las tarifas que devuelve cada cotización— que la remisión al correo no
crea.

**Lo que decide el abogado:** si esa remisión al correo satisface el deber de
información, o si prefiere la lista nombrada asumiendo el compromiso de
mantenerla.

---

## 3. En qué región procesa Resend

**Dónde está.** Dos sitios de `legales.privacidad`:

> Resend, como proveedor de correo transaccional, para enviarte los correos de
> verificación de cuenta, recuperación de contraseña y estado del pedido.

y, en transferencia internacional:

> Nuestra infraestructura se apoya en servicios de computación en la nube que
> pueden almacenar o procesar información en servidores ubicados fuera de
> Colombia; eso incluye al proveedor de infraestructura y al de correo
> transaccional.

**Qué hace el sistema.** Manda por SMTP el nombre de quien compra, su correo, el
número de pedido y —en el comprobante— el detalle de lo comprado y la dirección
de entrega. Desde `adr/0045` ese contenido también queda en reposo en la tabla
`correo_pendiente` hasta treinta días, en nuestra propia base.

### El dato, leído el 19 de septiembre de 2026

Era lo único que faltaba, y no era una cuestión de norma sino de contrato.

**Resend procesa en Estados Unidos.** Su acuerdo de tratamiento de datos lo dice
sin rodeos:

> Customer acknowledges that Company's primary processing operations take place
> in the United States, and that the transfer of Customer's Personal Data to the
> United States is necessary for the provision of the Services to Customer.

**Sus veintidós subencargados están todos en Estados Unidos**, según la lista que
el propio contrato manda consultar, actualizada el 27 de agosto de 2026. Entre
ellos AWS (alojamiento y envío), PlanetScale y Supabase (bases de datos), Vercel,
Cloudflare, Datadog y Stripe. Y dos que conviene mirar con atención porque no son
infraestructura: **Anthropic, PBC** («Artificial Intelligence») y **RunPod, Inc.**
(«Self-hosted LLMs»). La lista no dice a qué datos alcanzan.

**Los mecanismos de transferencia que Resend invoca no sirven aquí.** El contrato
se apoya en las cláusulas contractuales tipo de la Unión Europea, la adenda del
Reino Unido y el *EU-U.S. Data Privacy Framework*. Los tres son instrumentos
europeos y británicos: ninguno dice nada sobre una transferencia desde Colombia.

### Y con el dato en la mano, la pregunta cambió de forma

El art. 26 de la Ley 1581 de 2012 prohíbe transferir datos a países que no
ofrezcan un nivel adecuado de protección. Lo que no estaba mirado es que
**Colombia publica la lista de los que sí**: el numeral 3.2 del Capítulo Tercero
del Título V de la Circular Única de la SIC.

**Estados Unidos de América está en esa lista**, verificado en la compilación
oficial de la Circular 5 de 2017 y no de memoria:

> garantizan un nivel adecuado de protección los siguientes países: Alemania;
> Austria; Bélgica; Bulgaria; Chipre; Costa Rica; Croacia; Dinamarca; Eslovaquia;
> Eslovenia; Estonia; España; **Estados Unidos de América**; Finlandia; Francia;
> Grecia; Hungría; Irlanda; Islandia; Italia; Letonia; Lituania; Luxemburgo;
> Malta; México; Noruega; Países Bajos; Perú; Polonia; Portugal; Reino Unido;
> República Checa; República de Corea; Rumania; Serbia; Suecia; y los países que
> han sido declarados con nivel adecuado de protección por la Comisión Europea.

O sea que la transferencia a Resend **no necesita apoyarse en la autorización del
titular** —la excepción del art. 26 literal a, que es donde uno esperaría que
cayera— ni en una declaración de conformidad ante la Superintendencia. Cae en el
supuesto general, que es el camino cómodo. El parágrafo 2 del mismo numeral
describe qué tocaría hacer si el país no estuviera en la lista: verificar una
excepción del art. 26, acreditar los estándares del 3.1, o pedir la declaración
de conformidad. No es nuestro caso.

**Lo que no pude verificar, y va dicho para que no se lea como más firme de lo que
es.** La lista se leyó en la compilación oficial de la Circular 5 de 2017. La
versión consolidada del Título V que publica la SIC es un PDF escaneado que no se
deja leer, y existe además una Circular 2 de 2025 sobre transferencias
internacionales cuyo alcance no se pudo cotejar. Una versión posterior a 2017
añadió Australia y Japón **sin quitar a Estados Unidos**, así que todo apunta a
que sigue vigente — pero quien firme la política debería confirmarlo contra el
texto consolidado de hoy. Es exactamente el tipo de comprobación por la que se
paga un abogado.

**Recomendación.** Dejar la redacción genérica como está. Es cierta, y nombrar el
país no lo exige ninguna norma: el art. 12 de la Ley 1581 pide identificar al
responsable, y el art. 13 del Decreto 1377 enumera el contenido de la política
sin incluir la geografía del encargado. Nombrar «Estados Unidos» es opcional y
arrastra el mismo argumento en contra que nombrar a las transportadoras del punto
2: un dato concreto que caduca solo es peor que una descripción correcta que no
caduca.

**Lo que decide el abogado:**

1. Si con Estados Unidos en la lista de la SIC conviene nombrarlo en la política
   —lo que la haría más transparente y más frágil a la vez— o mantener la
   fórmula genérica.
2. Si los dos subencargados de inteligencia artificial de Resend piden algo más
   que la mención genérica. La pregunta previa es de hecho y no de derecho, y la
   lista no la contesta: **a qué datos alcanzan**. Por los correos pasan nombre,
   dirección de entrega y detalle de lo comprado.

**Qué cambia según la respuesta:** nombrar el país es una frase en
`legales.privacidad.secciones[8]` —el numeral **9**— en los dos idiomas, con su
subida de versión. Lo de los subencargados de IA puede no cambiar el texto y
sí el proveedor: si la respuesta es que hace falta más que la mención genérica y
Resend no puede acotar a qué datos alcanzan, lo que cambia es de dónde sale el
correo transaccional, que es un adaptador de `infrastructure` y no un párrafo.

**Qué hay que tener delante:** nada más. El contrato y la lista de subencargados
ya están leídos y citados arriba.

**Cerrado de este lado el 19 de septiembre de 2026.** Ya hay consulta que hacer.

---

## 4. "Despachamos a todo el territorio nacional"

**Dónde está.** `legales.terminos.secciones[7]` —el numeral **8**—, primer párrafo:

> Despachamos a todo el territorio nacional a través de empresas de transporte, y
> también puedes recoger tu pedido sin costo en nuestro punto de Medellín.

El mismo apartado ya matiza, dos párrafos más abajo:

> Si no hay empresa de transporte que cubra la dirección que indicaste, te lo
> decimos en ese momento y podrás recoger el pedido en nuestro punto.

**Qué hace el sistema.** Cuando ninguna transportadora cotiza, `CotizarEnvio`
lanza `EnvioSinCoberturaException`, `MetodosDePagoDisponibles` la atrapa y el
checkout ofrece solo la recogida, con un texto que lo explica. O sea: el
mecanismo funciona y está dicho. **Lo que no se sabe es a cuánta gente le toca.**

**Qué dice la norma.** El art. 5 numeral 13 de la Ley 1480 define publicidad
engañosa como aquella "cuyo mensaje no corresponda a la realidad **o sea
insuficiente**, de manera que induzca o pueda inducir a error, engaño o
confusión". La palabra que importa es *insuficiente*: una afirmación literalmente
cierta puede ser engañosa por lo que calla. Y el art. 29 le da fuerza vinculante:
"las condiciones objetivas y específicas anunciadas en la publicidad obligan al
anunciante, en los términos de dicha publicidad".

**El dato que faltaba, y ya está medido.** Hasta ahora esa frase descansaba sobre
una muestra de **dos ciudades** —Medellín y Bogotá, `docs/13` §6.5—. El 19 de
septiembre de 2026 se cotizaron los **1122 municipios** de la lista DIVIPOLA, con
y sin recaudo, contra la cuenta real (`docs/13` §6.18):

| | sin recaudo | con recaudo |
|---|---|---|
| **Cotiza** | **1044 (93,0 %)** | **1008 (89,8 %)** |
| Nadie cubre ese destino | 4 (0,4 %) | 40 (3,6 %) |
| El proveedor rechaza el código DANE | 74 (6,6 %) | 74 (6,6 %) |

**Solo cuatro municipios del país no tienen quien los cubra**: Los Andes (Nariño)
y tres de Guainía. Los otros 74 que no cotizan no son falta de cobertura: el
catálogo de Skydropx no tiene ese código DANE, y esa es una lista concreta para
pedirle a la plataforma que la complete.

**Recomendación.** La frase se sostiene. Nueve de cada diez municipios cotizan, y
el §8 ya trae el matiz del caso sin cobertura — lo único que conviene es
**acercarlo al primer párrafo**, para que quien lea "a todo el territorio
nacional" encuentre la excepción en la misma frase y no dos párrafos más abajo.
No hace falta reescribir la afirmación ni publicar una lista de municipios, que
además caducaría sola.

**Qué cambia según la respuesta:** acercar el matiz es reordenar los párrafos de
`legales.terminos.secciones[7]` en los dos idiomas. Cambiar la afirmación es
reescribir el primero. Las dos suben la versión; ninguna toca el código, que ya
ofrece solo la recogida cuando nadie cotiza.

**Lo que decide el abogado:** si con un 93 % medido y el matiz contiguo la
afirmación deja de ser "insuficiente" para el art. 5.13, o si prefiere una
formulación que no diga "todo".

---

## 5. Quién le devuelve las cuotas ya pagadas a quien compró con Sistecrédito

**El hecho.** Desde `ADR-0048` el sitio cobra con Sistecrédito, que no es un
medio de pago sino un **crédito de un tercero**: el comprador no nos paga a
nosotros, queda debiéndole cuotas a Sistecrédito, y a nosotros nos paga
Sistecrédito comprando la factura.

**Lo que eso rompe.** El derecho de retracto (Ley 1480 de 2011, art. 47) obliga
a resolver el contrato y a devolver "el dinero que el consumidor hubiese pagado".
Aquí el consumidor no nos pagó nada a nosotros. Lo que hay que deshacer es el
crédito y el pagaré, y **eso no lo podemos hacer nosotros**: la página oficial de
Sistecrédito lo describe como una reclamación que el comercio aliado debe
*solicitar*, y se tramita a mano en el portal Credinet. No hay API.

**El caso que no está resuelto.** Si el comprador ya le pagó una o más cuotas a
Sistecrédito antes de retractarse, esas sumas no las recibimos nosotros. La ley
dice que se devuelven "sin deducción alguna" y en un plazo que corre desde que se
ejerce el derecho; el dinero, sin embargo, lo tiene un tercero.

**Lo que decide el abogado:**

1. Si la obligación de devolver esas cuotas es nuestra —y entonces adelantamos
   el dinero y nos lo cobramos a Sistecrédito— o de Sistecrédito, y qué tiene que
   decir el contrato de vinculación para que eso quede claro.
2. Qué plazo nos obliga a nosotros cuando el trámite depende de la respuesta de
   un tercero, y qué hay que decirle al comprador mientras tanto.
3. Si los términos y condiciones publicados tienen que nombrar a Sistecrédito y
   explicar que el crédito lo otorga y lo anula él — hoy no lo hacen. **La
   política de datos sí, desde el 23 de septiembre de 2026** (numerales 5, 6 y
   8): lo que falta es la parte contractual, no la de datos.

**Qué cambia según la respuesta:** la 1 y la 2 no tocan texto publicado sino la
operación —quién adelanta el dinero y en cuánto tiempo— y, si nos obliga un
plazo propio, un reloj como el de `PlazosDeAtencion`, que hoy no distingue este
caso. La 3 añade un numeral a `legales.terminos` en los dos idiomas, con su
subida de versión.

**Lo que este expediente no puede resolver solo:** el contrato con Sistecrédito
no está leído en este punto. Antes de la consulta hay que tenerlo a la mano.

---

## 6. Si Sistecrédito es un encargado nuestro o un segundo responsable

> **Nació con número propio el 23 de septiembre de 2026.** Estuvo escrito en
> `docs/08` remitido "al punto 5 de `docs/14`", y el punto 5 nunca lo mencionó:
> una pregunta que se pierde sin que nadie la borre.

**El hecho.** Al pagar a cuotas, el checkout le manda a Sistecrédito el tipo y el
número de documento de quien compra —`SistecreditoClient.java:258-259`, `docType`
y `document`—. El dato **no se guarda** de nuestro lado: viaja en el comando y
termina ahí (`CrearIntentoDePagoSistecreditoComando`).

**Qué dice el texto publicado.** Desde el 23 de septiembre de 2026 la política lo
declara en tres sitios —el numeral 5, el 6 y el 8— y describe el hecho **sin
etiquetarlo**: dice que Sistecrédito estudia y otorga el crédito bajo su propia
política de tratamiento y su propio contrato contigo. Esa redacción informa al
titular igual en las dos hipótesis, y por eso el deber de informar se pudo cerrar
sin esperar la consulta.

**La pregunta.** Trata el documento para **su** finalidad —decidir y otorgar un
crédito, con su propio contrato con esa persona—, lo que apunta a segundo
responsable y no a encargado. La distinción no es académica: un encargado trata
por cuenta nuestra y lo que hace falta es un contrato de transmisión; un segundo
responsable trata por cuenta propia y entonces la **autorización** tiene que
cubrir esa comunicación con esa finalidad.

**Qué hace el sistema con la autorización hoy.** La casilla del checkout dice
"Autorizo el tratamiento de mis datos personales para procesar y entregar este
pedido" y no menciona el crédito. Al lado del campo del documento sí hay un aviso
en contexto —"Sistecrédito necesita tu documento para encontrar tu cupo. Se lo
enviamos a ellos para esta compra y no lo guardamos"—, que informa pero no es la
autorización.

**Lo que decide el abogado:** cuál de las dos figuras es, y si la casilla tiene
que decirlo.

**Qué cambia según la respuesta:** si es encargado, nada en el sitio y sí en el
contrato con Sistecrédito. Si es segundo responsable, cambia la clave
`checkout.autoriza_datos` en los dos idiomas —y con ella la versión de la
política que queda guardada en cada constancia de `autorizacion_datos`, porque lo
que se probó el día de la compra es el texto de esa versión—.

**Qué hay que tener delante:** el contrato de vinculación, el mismo del punto 5.

---

## Antes de la consulta

- [x] ~~Leer el contrato y la documentación de Resend y anotar la región de
      procesamiento en el punto 3.~~ Leído el 19 de septiembre de 2026:
      **Estados Unidos**, y ese país está en la lista de nivel adecuado de la
      SIC, así que la transferencia no depende de la autorización del titular.
- [x] ~~Anotar en el punto 4 el porcentaje de cobertura.~~ Medido el 19 de
      septiembre de 2026: 93,0 % con envío a domicilio.
- [ ] **Llevar el contrato de vinculación con Sistecrédito**, que los puntos 5 y 6
      necesitan y que este expediente no ha leído. De ahí salen además **dos**
      datos que el código espera: la comisión y quién la asume, y si exigen alguna
      leyenda o logo en el checkout. Eran tres: **el monto mínimo ya es dato**
      —$50.000, confirmado por el dueño— y sigue sin valor por omisión en
      `application.yml` a propósito, para que un despliegue que olvide la variable
      no arranque con el método encendido.
- [x] ~~Generar las hojas de los textos publicados en su versión vigente.~~
      Regeneradas el 23 de septiembre de 2026 con `npm run legales-impresos`:
      `docs/tramites/impresos/*-2026-09-23.html`, seis hojas. **Estaban
      desfasadas** —las que había eran de la versión `2026-09-18` y lo publicado
      era `2026-09-19`—, que es lo que pasa cuando la lista dice "falta
      imprimirlas" y el texto cambia debajo. **Falta imprimirlas**, y eso es lo
      único: se generan solas desde los mismos JSON que pinta el sitio, con la
      versión y la fecha en la cabecera y en el nombre del archivo.
- [ ] **Confirmar la razón social y el NIT de Sistecrédito** si se quiere
      nombrarlo en la política como se nombra a Skydropx. Hoy aparece solo con su
      nombre comercial, que es lo que está verificado; el NIT sale del mismo
      contrato de vinculación de la línea anterior. No es obligatorio: el art. 13
      del Decreto 1377 no exige enumerar destinatarios.

Este expediente lo preparó el proyecto, no un abogado. Los seis puntos de aquí y
los dos de `docs/12` §7 son decisiones de riesgo, y cada recomendación es un punto
de partida para la discusión, no una conclusión: **antes de cambiar una sola
línea de los textos publicados, esto lo revisa un abogado colegiado.**

Los cambios de texto del 23 de septiembre de 2026 —la contradicción de plazos y la
declaración de Sistecrédito— no son una excepción a esa regla: en los dos, lo que
se publicó es lo que la norma verificada dice o el hecho que el código ejecuta, no
una decisión de riesgo. Entran igual en la revisión, pero no la esperaban.
