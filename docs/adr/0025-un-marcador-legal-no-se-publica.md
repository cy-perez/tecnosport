# ADR 0025. Un marcador `[[ ]]` no se publica nunca

Fecha: 2026-09-10. Estado: aceptada.

## Contexto

La convención de marcar con `[[ ]]` el dato de negocio que falta viene de las
dos skills legales y es buena: impide inventar un plazo, un horario o el nombre
de un tercero. Un plazo inventado en el código es peor que uno inventado en un
párrafo, porque se ejecuta.

Lo que nadie escribió es qué pasa con el marcador **mientras** el dato no llega.
Y lo que pasaba es esto: la plantilla del documento legal pinta cada párrafo tal
como está (`documento-legal.page.html:29`), así que la página de términos
publicaba, palabra por palabra:

> El plazo de entrega es de `[[PLAZO DE ENTREGA REAL]]` días calendario contados
> desde la confirmación del pago.

Seis marcadores, en dos idiomas, durante una fase completa. Nada falló: ninguna
prueba mira el contenido de un texto legal, y `npm run verificar` no tenía por
qué enterarse.

Un documento así informa **peor** que uno que no prometa nada, y encima deja por
escrito, de puño del propio comerciante, que sabía que le faltaba el dato y
publicó igual.

Al revisar los seis apareció algo que agrava el diagnóstico: **cuatro no eran
datos del negocio.** Dos los responde la ley (quién paga el flete de la
devolución, el término de garantía de un celular) y dos estaban ya decididos en
el repositorio, en un ADR y en un documento de infraestructura, sin que el texto
se enterara. En el caso del flete, `docs/12-legales-de-envio.md` traía la
respuesta escrita desde el 8 de septiembre y el marcador siguió publicado.

## Decisión

**Un marcador es una anotación para quien audita y para quien programa. No llega
a la pantalla.** Tres reglas:

1. **Antes de preguntarle al negocio, se clasifica el dato que falta** en cuatro
   clases: normativo público, ya decidido en el repositorio, con respuesta en la
   norma, o de verdad del negocio. Solo la cuarta se pregunta. Clasificar mal no
   retrasa un marcador: lo congela, porque nadie va a decidir un festivo.
2. **Mientras el dato no llegue hay dos salidas y solo dos**: quitar la promesa
   concreta y dejar la genérica que sí es verdad, o declarar el mínimo legal
   supletivo **diciendo que es el legal**. Inventar un valor plausible no es la
   tercera salida: convierte un dato faltante en un incumplimiento con prueba
   escrita.
3. **Cerrar un marcador arrastra seis cosas**, no una: el otro idioma, la
   versión y la vigencia del documento, la versión que el servidor sella en cada
   constancia de autorización, el código que respondía "no se sabe" mientras el
   dato faltaba, su prueba —que cambia de sentido y no solo de valor— y la
   guarda.

La regla la sostiene `tools/verificar-marcadores.mjs`, dentro de
`npm run verificar`. Solo mira `apps/web/src/assets/i18n`: un marcador en un
comentario de código es correcto y útil.

El procedimiento completo vive en la skill, no aquí:
`.claude/skills/vacios-legales-del-sitio/references/cerrar-marcadores.md`.

## Alternativas

**Filtrar los marcadores en la plantilla**, ocultando el párrafo o el fragmento
que lleve uno. Se descartó: convierte un incumplimiento visible en uno
invisible. Un párrafo que desaparece de la política de datos sigue siendo
información que la ley exige dar, y nadie se enteraría de que falta.

**Una prueba en vez de una guarda.** Una prueba por documento que compruebe que
su texto no trae `[[` habría funcionado, pero se ejecuta con la batería del
frontend y el problema no es del frontend: es del contenido publicado. La guarda
cuesta menos de un segundo, corre antes del lint y señala ruta y línea.

**Dejarlo en la revisión humana.** Es lo que había, y produjo seis marcadores
publicados durante una fase.

## Consecuencias

- Ningún documento legal se publica con un marcador. Los tres datos de negocio
  que siguen abiertos —plazo de entrega real, horario de atención y proveedor de
  correo transaccional de producción— están hoy cubiertos con una de las dos
  salidas, y el texto no miente en ninguno de los dos idiomas.
- **Cambiar un texto legal ahora obliga a subir la versión en tres sitios**: los
  dos JSON y `politica-datos-version`. Las constancias de autorización ya
  guardadas conservan su versión como prueba histórica de lo que esa persona
  aceptó ese día; **no hay flujo de re-solicitud de autorización**, y esta
  decisión no lo crea. Si algún cambio futuro altera las finalidades del
  tratamiento —y no solo la redacción—, esa decisión hay que tomarla aparte.
- La guarda se comprobó metiendo un marcador a propósito. En este proyecto ya
  hubo un guardián configurado con sintaxis legada que el plugin aceptaba sin
  aplicar (regla dura #1 de `CLAUDE.md`); uno sin verificar no cuenta.
