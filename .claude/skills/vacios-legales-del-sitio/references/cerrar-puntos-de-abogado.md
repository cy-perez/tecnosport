# Cerrar los puntos para abogado que dejó una auditoría anterior

Es la operación inversa de la Fase 6. Allí se **abren** puntos; aquí se revisan
los que ya están abiertos, y se retiran los que no merecen la hora.

Importa porque un punto para abogado es más silencioso que un marcador `[[ ]]`.
El marcador al menos afea el documento: alguien lo ve. Un punto para abogado vive
en un archivo de `docs/`, no rompe ninguna prueba, no sale en ningún informe, y
tiene una propiedad que lo vuelve inmortal: **está dirigido a otra persona**. Nadie
lo cierra porque nadie cree que le toque.

Y se acumulan en sitios distintos. Cuando la lista vive en tres documentos, el
expediente que se manda es el de uno solo, y los otros dos siguen ahí después de
pagar la consulta.

## Por qué caducan

Un punto para abogado se escribe en el momento de mayor ignorancia sobre el
asunto: justo cuando se descubre. Entre ese día y el de la consulta pasan cosas
que lo cambian sin que nadie lo edite.

- **El texto publicado cambió** y tomó una de las dos salidas que el punto
  planteaba. La pregunta ya no es "cuál elegimos" sino "confirmar la elegida", que
  es otra pregunta y más barata.
- **Se midió lo que no se sabía.** Muchos puntos tienen una premisa de hecho
  —"cuando la transportadora suma su comisión de recaudo"— que se escribió como
  supuesto. Si la medición la desmiente, el punto no se contesta: se retira.
- **Se leyó la norma entera.** El punto se abrió porque un artículo no se había
  buscado.
- **El código resolvió la pregunta.** El caso de uso ya distingue los tres plazos
  y no hay nada que decidir.
- **Otro punto se lo tragó.** Dos documentos preguntan lo mismo con otras
  palabras.

## El triaje: cuatro clases, y solo una se manda

Antes de meter un punto en el expediente, clasifícalo. El orden importa: las tres
primeras clases se cierran aquí.

**1. La norma lo contesta, y se puede verificar.** El punto se abrió por
desconocimiento, no por ambigüedad. Se verifica en fuente oficial —con la Fase 0,
sin excepción— y se cierra citando lo verificado y la fecha.

Ejemplo real: "evaluar si hay que registrar la base de datos ante la SIC,
consultarlo con un contador o abogado". El Decreto 090 de 2018 dice quién está
obligado, el dato que faltaba —que el sitio lo opera una persona natural— estaba
en el propio documento publicado, y la respuesta es que no aplica. Nadie tenía que
decidir nada.

Cuidado con la trampa de esta clase: **que la norma conteste no significa que tú
la recuerdes**. Un punto retirado citando un artículo de memoria es peor que el
punto abierto, porque ahora hay una respuesta escrita y falsa. Sin verificación en
fuente oficial, el punto se queda.

**2. La premisa de hecho es medible.** El punto pregunta por una consecuencia
jurídica de un hecho que nadie comprobó. Mide el hecho primero. Si la premisa era
falsa, el punto se retira con la medición al lado; si era cierta, el punto
sobrevive y ahora lleva un dato en vez de un supuesto — que es lo que hace que una
consulta se conteste en diez minutos y no en una reunión.

**3. Es una decisión del negocio, no de derecho.** "Confirmar si el negocio quiere
sostener la posición contraria, sabiendo que la duda se interpreta a favor del
consumidor" no es una pregunta para un abogado: el abogado ya dijo lo que había
que decir —cuál es la posición segura— y lo que queda es si el dueño quiere
asumir el riesgo de la otra. Estos puntos no se cierran: **se mudan**, a la lista
de lo que decide el dueño. Mezclarlos con los jurídicos hace que la consulta
parezca más grande de lo que es y que el dueño no vea lo que le toca.

**4. Es de verdad una decisión de riesgo jurídico.** Queda, y entonces se le exige
lo de la sección siguiente.

## Lo que tiene que llevar un punto que sobrevive

Un punto que sobrevive al triaje no se deja como estaba. Lo que convierte una
respuesta en un cambio —y no en una nota que alguien tendrá que interpretar tres
semanas después— es esto:

- **La disyuntiva, explicada y sin resolver**, con la recomendación del proyecto y
  su motivo.
- **Qué dice hoy el texto publicado, literal**, y dónde vive: archivo y clave
  exacta, no el número del apartado que se lee en pantalla. Los dos se desfasan, y
  el que se desfasa en silencio es el de la pantalla.
- **Qué hace el sistema**, con ruta y línea. Un punto que no dice qué hace el
  código invita a contestar sobre un sitio imaginario.
- **Qué cambia según cada respuesta.** Si contesta A, esta clave en los dos
  idiomas, la versión y la vigencia del documento, y la constancia de autorización
  que guarda esa versión. Si contesta B, nada. Sin esto, la respuesta llega y el
  trabajo de traducirla empieza de cero.
- **Qué hay que tener delante** para que la pregunta se pueda contestar: un
  contrato sin leer, una tarifa, una medición. Un punto que necesita un documento
  que nadie ha pedido no está listo para mandarse, y decirlo es más útil que
  mandarlo igual.

## Cómo se retira un punto

Igual que un marcador: **se tacha, no se borra.** Queda el enunciado original, la
razón por la que se retiró, la fuente verificada o la medición, y la fecha. Un
punto borrado vuelve a nacer dentro de seis meses porque el motivo que lo abrió
sigue ahí y ya nadie se acuerda de que se había contestado.

Y cuando lo retirado estaba escrito en otro documento —un `docs/` distinto, un
ADR, un comentario de configuración—, **el otro documento también se corrige**. Un
punto cerrado en un sitio y vivo en otro es un punto vivo.

## Lo que este triaje no es

No es dar asesoría jurídica ni reemplazar la consulta. Un punto se retira cuando
**la norma verificada o una medición lo contestan**, no cuando parece improbable,
no cuando el riesgo se ve pequeño y no cuando llevarlo saldría caro. Si después
del triaje el punto sigue siendo una decisión de riesgo, va al abogado aunque la
respuesta parezca obvia: lo obvio es justo lo que nadie verifica.

Y al revés: mandar al abogado una pregunta que la norma contesta no es prudencia.
Gasta la hora que necesitan los puntos que sí la merecen, y enseña a quien recibe
el expediente que la lista no está filtrada.
