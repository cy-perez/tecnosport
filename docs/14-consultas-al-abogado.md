# Consultas al abogado

Expediente interno. **No es asesoría jurídica y no se publica**: es la lista de
decisiones que este proyecto no puede tomar solo, preparadas para que un abogado
colegiado las resuelva en una hora en vez de leerse el sitio entero.

`docs/09-plan-de-arranque.md` las venía arrastrando desde el 10 de septiembre de
2026 como tres preguntas sueltas dentro de una entrada. Así no se le entregan a
nadie. Cada una lleva aquí lo mismo: **qué dice hoy el texto publicado, qué hace
el sistema, qué dice la norma verificada, y qué recomienda este proyecto.**

Las normas se verificaron contra el texto vigente en el SISJUR de la Alcaldía de
Bogotá el 19 de septiembre de 2026, no de memoria, y la lista de países con nivel
adecuado de protección contra la compilación oficial de la Circular 5 de 2017 de
la SIC. Donde no hay cita es porque no se encontró norma que lo resuelva, y eso
también es un dato. Donde la fuente no se dejó leer —el Título V consolidado es un
PDF escaneado— queda dicho en el punto que lo necesita, en vez de citarlo como si
se hubiera leído.

---

## 1. "Desgaste normal" como exclusión de la garantía

**Dónde está.** `legales.terminos.secciones[10]`, en los dos idiomas:

> La garantía no cubre el daño causado por uso indebido, modificación no
> autorizada, desgaste normal ni fuerza mayor.

**Qué hace el sistema.** Nada automático: la garantía se atiende por el flujo de
`garantia` del panel, y quien decide si un caso entra o no entra es una persona.
O sea que esta frase no es decorativa — es el criterio con el que se va a
rechazar una reclamación real.

**Qué dice la norma.** El art. 16 de la Ley 1480 de 2011 enumera las causales de
exoneración y son cuatro: fuerza mayor, caso fortuito, hecho de un tercero, y el
uso indebido del bien o el incumplimiento de las instrucciones de instalación,
uso o mantenimiento. **"Desgaste normal" no aparece**, ni ahí ni en los arts. 7 y
8. Tres de las cuatro que el texto enumera sí están en la ley; la cuarta la
añadimos nosotros.

Y hay un agravante de método: la Ley 1480 es de orden público y se interpreta a
favor del consumidor. Una exclusión más amplia que la legal no solo es ineficaz
—no se puede oponer—, sino que en un expediente de la SIC se lee como cláusula
abusiva, lo que empeora la posición del negocio en toda la disputa, no solo en
ese punto.

**La tensión real, que es la que hay que resolver.** El desgaste normal sí
importa comercialmente: una batería de celular que pierde capacidad con los
ciclos, o unos tenis con la suela gastada al año, no son un defecto de calidad ni
de idoneidad. La pregunta no es si se puede excluir —no se puede, con ese
nombre—; es **cómo se dice lo que sí es cierto**: que la garantía cubre defectos,
y que el deterioro esperable por el uso normal durante la vida útil del producto
no es un defecto.

**Recomendación.** Quitar "desgaste normal" de la lista de exclusiones y, si el
abogado lo avala, reformular en positivo dentro de la definición de qué cubre la
garantía, sin presentarlo como una causal de exoneración. Las otras tres se
quedan como están.

**Lo que decide el abogado:** si esa reformulación en positivo es defendible o si
lo prudente es sencillamente no decir nada sobre desgaste y resolver caso por
caso.

---

## 2. Nombrar o no a las transportadoras que subcontrata Skydropx

**Dónde está.** `legales.privacidad.secciones[7]`, la lista de a quién se le
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

**Cerrado de este lado el 19 de septiembre de 2026.** Ya hay consulta que hacer.

---

## 4. "Despachamos a todo el territorio nacional"

**Dónde está.** `legales.terminos.secciones[7]`, primer párrafo:

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

**Lo que decide el abogado:** si con un 93 % medido y el matiz contiguo la
afirmación deja de ser "insuficiente" para el art. 5.13, o si prefiere una
formulación que no diga "todo".

---

## Antes de la consulta

- [x] ~~Leer el contrato y la documentación de Resend y anotar la región de
      procesamiento en el punto 3.~~ Leído el 19 de septiembre de 2026:
      **Estados Unidos**, y ese país está en la lista de nivel adecuado de la
      SIC, así que la transferencia no depende de la autorización del titular.
- [x] ~~Anotar en el punto 4 el porcentaje de cobertura.~~ Medido el 19 de
      septiembre de 2026: 93,0 % con envío a domicilio.
- [ ] Llevar impresos los dos textos legales publicados, en su versión vigente y
      con su fecha, no una transcripción. **Las hojas ya se generan solas**:
      `npm run legales-impresos` las escribe en `docs/tramites/impresos/` leyendo
      los mismos JSON que pinta el sitio, con la versión y la fecha en la cabecera
      y en el nombre del archivo. Falta imprimirlas.

Este expediente lo preparó el proyecto, no un abogado. Los cuatro puntos son
decisiones de riesgo y las cuatro recomendaciones son un punto de partida para la
discusión, no una conclusión: **antes de cambiar una sola línea de los textos
publicados, esto lo revisa un abogado colegiado.**
