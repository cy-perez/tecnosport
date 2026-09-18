# ADR-0041 — El negocio no es responsable de IVA

Fecha: 2026-09-18
Estado: aceptado
Relacionados: `adr/0040`, `docs/00-producto.md`, `docs/02-modelo-datos.md`,
`docs/08-seguridad-legal.md`, `docs/12-legales-de-envio.md` §4

## Contexto

`adr/0040` cerró el 18 de septiembre la pregunta del IVA sobre el flete, y en su sección "Lo que
esto NO arregla" dejó escrita otra, más básica, que nadie había hecho nunca:

> **Nadie ha confirmado que el negocio sea responsable de IVA.** No está escrito en ningún documento
> del proyecto, y el sistema ya lo asume por todas partes.

Y lo asumía de verdad: el catálogo sembrado llevaba `tasa_iva = 0.19` en sus ocho variantes, el
panel proponía `0.19` por omisión al dar de alta una variante nueva, y el sitio publicaba en dos
sitios —el resumen del checkout y el numeral 4 de los términos— que **los precios incluyen IVA**.
Tres capas construidas sobre una premisa que nadie había verificado.

Preguntado el mismo día: **el negocio no es responsable de IVA**, y está a nombre de una persona
natural (el NIT del pie, `1054994043-9`, es una cédula). Las dos cosas van juntas: solo una persona
natural puede ser no responsable, por el parágrafo 3 del artículo 437 del Estatuto Tributario.

## Decisión

**El sistema declara que el negocio no es responsable de IVA, ninguna variante puede llevar tasa
distinta de cero, y el sitio deja de afirmar que los precios lo incluyen.**

La condición vive en configuración, `NEGOCIO_RESPONSABLE_IVA`, hoy en `false`.

## Por qué la frase publicada no era solo un dato viejo

Esta es la parte que cambió el peso de la tarea. El **literal a del artículo 1.3.1.15.2 del Decreto
1625 de 2016** le prohíbe a un no responsable *adicionar al precio de los bienes que venda suma
alguna por concepto del impuesto sobre las ventas*, **y añade que si lo hiciere queda obligado a
cumplir íntegramente con las obligaciones de quienes sí son responsables**.

O sea que la consecuencia de equivocarse aquí no es una corrección de texto: es un cambio de régimen
tributario. Por eso la tasa no se deja en manos de un campo de formulario —un `0.19` tecleado por
inercia en el panel tendría esa consecuencia— y por eso la guarda vive en `AgregarVariante`, antes
de tocar el repositorio, con `NEGOCIO_RESPONSABLE_IVA` como fuente.

## Qué exige el art. 26 de la Ley 1480, que es otra cosa

El Estatuto del Consumidor obliga a informar *"el precio de venta al público, incluidos todos los
impuestos y costos adicionales"*, y que **el consumidor solo esté obligado a pagar el precio
anunciado**. Exige que el número publicado sea el total; **no exige nombrar los impuestos**. Con un
no responsable, el precio publicado ya es el total sin decir una palabra del IVA.

Y su segundo inciso es el que sostiene el modelo de precio base más flete que la Fase 7 montó: los
costos adicionales por transporte *"deberá ser informada adecuadamente, especificando el motivo y el
valor"*. Es exactamente lo que hace el checkout. Ese razonamiento estaba hecho desde el 14 de
septiembre y no estaba escrito en ninguna parte; queda aquí.

**La Ley 2439 de 2024 no tocó el art. 26** — modificó los arts. 5, 45, 50, 51 y 52.

## No hay obligación de anunciarse, y se buscó

El artículo 506 del Estatuto Tributario obligaba al antiguo régimen simplificado a exhibir su
inscripción en el RUT. **Está derogado** por el art. 122 de la Ley 1943 de 2018 y el art. 160 de la
Ley 2010 de 2019. Hoy ninguna norma obliga a declarar la condición en el sitio.

Aun así los términos la mencionan, y es una decisión de redacción y no de cumplimiento: explica por
qué no aparece ningún impuesto en un desglose donde el comprador colombiano espera verlo. En el
checkout no se menciona, porque ahí sería ruido.

## Consecuencias

- `tasa_iva` queda en `0.00` en todas las variantes (`V50`), y el sembrador ya **no puede** escribir
  otra cosa: la tasa dejó de ser un parámetro de `guardarVariante`.
- `AgregarVariante` rechaza con `TASA_IVA_NO_PERMITIDA` (422) cualquier tasa distinta de cero
  mientras la configuración diga que no es responsable.
- El formulario del panel propone `0` en vez de `0.19`. **El campo no se borra**: ver abajo.
- El resumen del checkout dice "El precio de cada producto es el valor final: no se le suma ningún
  impuesto"; el numeral 4 de los términos lo explica y nombra la condición.
- La versión legal publicada sube a `2026-09-18` en sus tres copias, porque **esta vez sí cambió la
  política de datos** —dos frases hablaban de "facturación" y el negocio no factura—, y el
  precedente de `docs/12` §3 dice que la versión solo se queda quieta cuando la política no se
  toca.

## Lo que NO se hizo

- **`linea_pedido.tasa_iva` no se reescribe hacia atrás.** Esa columna guarda la tasa con la que se
  cobró un pedido, no la que se cobra hoy; corregirla falsificaría el registro de lo que el
  comprador pagó. Hoy no hay ningún pedido real, así que no cuesta nada, y sigue siendo lo correcto
  el día que los haya.
- **No hay `check (tasa_iva = 0)` en la base.** La calidad de no responsable se pierde desde el
  período siguiente a incumplir cualquiera de las condiciones del parágrafo 3 —entre ellas un tope
  anual de ingresos brutos en UVT—, y ese día un `check` convertiría un cambio de configuración en
  una migración urgente bajo presión.
- **La columna `tasa_iva` no se borra**, por lo mismo.

## Qué le cambia a `adr/0040`

Le cambia la premisa, no la conclusión. `adr/0040` decidió que el flete cobrado no se grava
razonando sobre el art. 447 y el Concepto DIAN 4945 de 2025, y asumió el riesgo de que la DIAN
leyera el artículo al revés. **Siendo no responsable no hay base gravable que integrar en ninguna
línea**, así que la decisión sobrevive y el riesgo que aquel ADR cuantificaba desaparece mientras
esta condición se mantenga.

Es la cuarta vez en este proyecto que una conclusión correcta descansaba sobre un razonamiento que
no era el que la sostenía. Las tres anteriores están en `docs/09`.

## Qué lo reabre

1. **Cruzar cualquiera de los topes del parágrafo 3 del art. 437.** Es lo esperable si la tienda
   crece, y no es una decisión: ocurre. Ese día `NEGOCIO_RESPONSABLE_IVA` pasa a `true`, las tasas
   del catálogo dejan de ser cero, los dos textos publicados cambian con ellas, y `adr/0040` vuelve
   a quedar expuesto tal como lo escribió.
2. **Un concepto de contador** que diga que la condición no se cumple hoy.

Nadie firmó esta lectura: la confirmó el dueño del negocio y la verificó este documento contra las
normas citadas. No dice "revisado por un contador" porque ninguno lo revisó.
