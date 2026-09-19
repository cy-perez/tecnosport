# Consultas al abogado

Expediente interno. **No es asesoría jurídica y no se publica**: es la lista de
decisiones que este proyecto no puede tomar solo, preparadas para que un abogado
colegiado las resuelva en una hora en vez de leerse el sitio entero.

`docs/09-plan-de-arranque.md` las venía arrastrando desde el 10 de septiembre de
2026 como tres preguntas sueltas dentro de una entrada. Así no se le entregan a
nadie. Cada una lleva aquí lo mismo: **qué dice hoy el texto publicado, qué hace
el sistema, qué dice la norma verificada, y qué recomienda este proyecto.**

Las normas se verificaron contra el texto vigente en el SISJUR de la Alcaldía de
Bogotá el 19 de septiembre de 2026, no de memoria. Donde no hay cita es porque no
se encontró norma que lo resuelva, y eso también es un dato.

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

**Qué falta, y no es una cuestión de norma.** Es un **dato de contrato**: en qué
región procesa Resend y qué dice su DPA. Nadie lo ha mirado. La política ya cubre
el caso diciendo "pueden estar fuera de Colombia", que es una afirmación
prudente y probablemente cierta, pero **está escrita sin haberlo comprobado**, y
este proyecto tiene la costumbre de no dejar pasar eso.

**Recomendación.** Antes de la consulta con el abogado, alguien tiene que leer el
contrato y la documentación de Resend y anotar aquí la región. Con el dato en la
mano, la pregunta al abogado es corta: si la redacción genérica actual basta o si
conviene nombrar el país. Sin el dato, no hay consulta que hacer.

**Pendiente de este lado, no del abogado.**

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

**El dato que faltaba, y ya no falta.** Hasta ahora esa frase descansaba sobre
una muestra de **dos ciudades** —Medellín y Bogotá, `docs/13` §6.5—. El 19 de
septiembre de 2026 se midió la cobertura real cotizando los **1122 municipios**
de la lista DIVIPOLA, con y sin recaudo, contra la cuenta real. El resultado está
en `docs/13` §6.18 y el número concreto es lo que decide esta pregunta.

**Recomendación**, según lo que diga la medición:

- **Si la cobertura es casi total**, la frase se sostiene con el matiz que el §8
  ya trae, y basta con acercarlo al primer párrafo para que no quede a dos
  párrafos de distancia.
- **Si hay un hueco material**, la frase hay que reescribirla. No con una lista de
  municipios —caduca sola, igual que la de transportadoras— sino diciendo que la
  cobertura depende del destino y que se confirma en el checkout antes de pagar,
  que es exactamente lo que el sistema hace.

**Lo que decide el abogado:** dónde está el umbral entre "cierto con matiz" y
"insuficiente" para el art. 5.13, con el porcentaje medido delante.

---

## Antes de la consulta

- [ ] Leer el contrato y la documentación de Resend y anotar la región de
      procesamiento en el punto 3.
- [ ] Anotar en el punto 4 el porcentaje de cobertura que arroje `docs/13` §6.18.
- [ ] Llevar impresos los dos textos legales publicados, en su versión vigente y
      con su fecha, no una transcripción.

Este expediente lo preparó el proyecto, no un abogado. Los cuatro puntos son
decisiones de riesgo y las cuatro recomendaciones son un punto de partida para la
discusión, no una conclusión: **antes de cambiar una sola línea de los textos
publicados, esto lo revisa un abogado colegiado.**
