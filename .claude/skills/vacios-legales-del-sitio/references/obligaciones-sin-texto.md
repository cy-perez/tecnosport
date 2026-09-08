# Obligaciones que existen aunque el documento calle

Un sitio puede tener textos impecables y aun así incumplir, porque hay
obligaciones que no nacen de haberlas escrito. Recorre esta lista **entera** en
cada auditoría, incluso cuando ningún documento las mencione: no mencionarlas no
exime, y además incumple por separado el deber de información.

El detalle normativo y su vigencia están en `references/marco-normativo.md` de la
skill `textos-legales-comerciales`. Aquí está lo que cada obligación exige del
sistema construido.

---

## 1. Información del vendedor visible antes de comprar

Nombre o razón social, identificación tributaria, dirección física en Colombia,
teléfono y correo. **Antes** de comprar, no en una subpágina que solo se alcanza
desde el pie.

Qué comprobar: que el checkout, y no solo el pie, permita llegar a esa
información; y que el dato sea correcto, empezando por el dígito de verificación
del NIT.

---

## 1.1. Resumen del pedido antes de finalizar la transacción

En comercio electrónico hay que mostrarle al comprador, **antes** de que termine
la compra, un resumen con la descripción de lo que va a adquirir, el precio
individual, el precio total, **los costos adicionales de envío informados de
forma adecuada y separada**, y la suma total a pagar. No depende de que ningún
documento lo prometa. Verifica el artículo vigente en `marco-normativo.md` antes
de citarlo.

Es la obligación que más se incumple sin querer, porque la pantalla que la
incumple suele estar bien hecha: muestra un "Subtotal" y un botón. Mientras el
envío va incluido en el precio, subtotal y total coinciden y nadie nota que falta
el total ni la línea de envío. El día que el negocio decide cobrar flete aparte,
esa misma pantalla pasa a incumplir sin que nadie la haya tocado.

Qué comprobar en el sistema:

- Que exista una línea de **envío** con una cifra, y una de **total a pagar**,
  distintas del subtotal, en la pantalla donde se paga.
- Que ningún cargo aparezca **después** de aceptar el total: ni un flete
  recalculado al despachar, ni una comisión trasladada, ni un ajuste por peso
  real.
- Que las cifras las calcule el servidor. Ver la ficha de costo de envío en
  `promesas-y-su-rastro.md`.

---

## 2. Reversión del pago

Existe aunque los términos no la nombren. Ver la ficha en
`promesas-y-su-rastro.md`. Lo importante aquí: **no basta con no prohibirla**;
hay un deber de facilitar el trámite, y facilitarlo implica que exista un camino.

---

## 3. Garantía legal

No se pacta: se debe. Y las exclusiones que el documento invente más allá de la
ley no solo son ineficaces, **agravan la posición del negocio** porque señalan
mala fe.

Qué comprobar en el sistema: que ninguna pantalla, correo o texto de producto
anuncie una exclusión más amplia que la legal. Esto se busca en los textos de
producto y en los correos transaccionales, no solo en los documentos legales.

---

## 4. Plazos de respuesta a consultas y reclamos de datos personales

Los plazos corren desde que llega la solicitud, exista o no una pantalla para
recibirla. Verifica los plazos vigentes antes de auditar.

Qué comprobar: que el canal exista y que alguien mida el tiempo. Un buzón sin
métrica no puede demostrar que respondió en plazo. Si no hay registro de cuándo
llegó cada solicitud, el incumplimiento es indemostrable en las dos direcciones —
y quien tiene la carga de probar que cumplió es el responsable.

---

## 4.1. La entrega dispara plazos, y hay que saber cuándo ocurrió

El retracto y la garantía se cuentan **desde la entrega**. Eso convierte la fecha
de entrega en un dato legal, no operativo, y obliga a preguntar algo que nadie
pregunta: *¿quién la escribe, y qué pasa si nadie la escribe?*

Qué comprobar:

- Que la fecha exista, con su origen (humano, transportadora, tarea programada).
- Que un evento perdido del proveedor no deje el pedido sin fecha de entrega
  indefinidamente. Si el único camino es un webhook, falta la red de seguridad.
- Que los costos de la devolución y del reintegro estén repartidos como la ley
  los reparte, y no como convenga: en Colombia el transporte de la devolución lo
  cubre el consumidor, pero los gastos de devolver el dinero no. **Verifica los
  dos antes de afirmarlos.**

Detalle del rastro en `promesas-y-su-rastro.md`.

---

## 5. La publicidad obliga

Lo anunciado se vuelve exigible. Eso convierte en auditables lugares que nadie
considera legales:

- Las descripciones de producto.
- Los textos de la portada y de las campañas.
- Los correos transaccionales y promocionales.
- Cualquier cifra en una insignia: "envío gratis", "entrega en 24 horas",
  "garantía de por vida".
- **Las etiquetas de las opciones de entrega y de pago**: "sin costo", "gratis",
  "recoge y ahorra". Un "sin costo" es exigible, y también es **falso** si el
  costo está embebido en el precio y no se evita al elegir esa opción.

Qué comprobar: que ninguna cifra prometida en la interfaz contradiga el documento
legal ni la operación. Una insignia de "entrega en 24 h" junto a un documento que
dice 30 días calendario es una contradicción que el comprador leerá a su favor, y
la ley lo respalda.

---

## 6. Datos de menores y datos sensibles

Reglas más estrictas. Si el catálogo puede atraer compradores menores, o si algún
formulario recoge datos sensibles, aplica.

Qué comprobar: qué campos se piden de verdad en los formularios. Un campo de
fecha de nacimiento, de talla infantil o de condición de salud cambia el análisis.

---

## 7. Transferencia internacional de datos

Casi siempre aplica, porque casi siempre hay nube o SaaS extranjero. Régimen
propio, con distinción entre transferencia y transmisión.

Qué comprobar: dónde corren de verdad la aplicación, la base de datos, el
almacenamiento de imágenes y el proveedor de correo. La región del despliegue es
un hecho verificable en la infraestructura, no una suposición. **Punto para
revisión de abogado.**

---

## 8. Registro Nacional de Bases de Datos

Aclara la confusión en la entrega, porque es muy extendida: el universo de
obligados a **registrar** se redujo, y las personas naturales y las micro y
pequeñas empresas normalmente no lo están. Pero **no estar obligado a registrar
no exime de cumplir el régimen de datos personales**. Verifica los umbrales
vigentes antes de afirmar cualquiera de las dos cosas.

---

## 9. Seguridad de la información y reporte de incidentes

La SIC emite instrucciones vinculantes sobre medidas de seguridad y reporte de
incidentes. Verifica las circulares vigentes.

Qué comprobar en el sistema, como mínimo: que las claves no se guarden
recuperables, que los tokens tengan vencimiento, que exista límite de intentos,
que las cookies de sesión sean `HttpOnly`, y que haya alguna forma de enterarse
de un incidente. No se puede reportar en plazo lo que no se detecta.

---

## 10. Evidencia de la aceptación

No es una obligación con nombre propio, es la condición para poder probar todas
las demás. El día de la reclamación hay que poder responder: **qué decía el
documento el día de esa compra, y qué aceptó esa persona.**

Qué comprobar:

- Que cada aceptación guarde la **versión** del texto, no un booleano.
- Que las versiones anteriores del texto se conserven, o al menos se puedan
  reconstruir desde el control de versiones con su fecha.
- Que la versión la fije el servidor.
- Que el idioma en que se mostró el documento no cambie qué se aceptó — o bien
  porque se guarda, o bien porque una nota declara cuál versión rige.

Un sistema que guarda `acepto = true` no tiene evidencia de nada.

---

## 11. Conservación y borrado

Dos obligaciones que tiran en direcciones opuestas: conservar lo que la ley
tributaria y contable exige, y no conservar datos personales más allá de la
finalidad.

Qué comprobar: que exista una decisión escrita sobre qué se conserva, cuánto y
por qué; y que las tareas de purga que ya existan —carritos, tokens vencidos,
sesiones— no borren nada que haya que conservar, ni conserven indefinidamente lo
que no.

---

## 12. Accesibilidad de la información

El deber de información es de información **comprensible**. Un documento legal
que no se puede leer con lector de pantalla, o cuyo contraste no alcanza, informa
peor.

Qué comprobar: que las páginas legales pasen la auditoría de accesibilidad como
cualquier otra pantalla. Son, además, las que alguien va a leer justo cuando
tiene un problema con una compra.
