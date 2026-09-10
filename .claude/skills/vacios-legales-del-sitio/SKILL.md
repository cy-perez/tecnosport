---
name: vacios-legales-del-sitio
description: Audita el sitio construido contra lo que sus propios documentos legales prometen y contra lo que la ley colombiana exige aunque el documento calle, y cierra los huecos en el código. Úsala cuando alguien pregunte "¿qué nos falta legalmente para abrir?", "¿el sitio cumple lo que dicen los términos?", "¿podemos publicar ya?", "revisa los vacíos legales", "qué promete el texto que el sistema no hace", "auditoría legal del sitio", "coherencia entre los legales y el código"; cuando haya que implementar retracto, reembolso, reversión del pago, garantía, PQR, ejercicio de derechos del titular o eliminación de cuenta; cuando se vaya a lanzar o mover el DNS a producción; cuando cambie el modelo de cobro o de entrega —cotizar el envío, cobrar el flete aparte del precio, ofrecer recogida sin costo, cambiar de transportadora o de plataforma logística, activar el recaudo contra entrega— y haya que revisar qué promesas de costo y de plazo quedan falsas; cuando un texto legal cambie y haya que ver qué código arrastra; o cuando haya que cerrar los marcadores `[[ ]]` que dejó una auditoría anterior — "cierra los datos pendientes", "qué nos falta por decidir", "resuelve los marcadores de los textos legales", "el documento publicado tiene un marcador a la vista". Complementa a `textos-legales-comerciales`, que redacta los documentos: esta hace que el software los cumpla. Covers Colombian e-commerce compliance gap analysis, mapping legal promises to code, retracto, reversión del pago, garantía legal, habeas data rights implementation.
---

# Vacíos legales del sitio — de la promesa al código

Un documento legal correcto y un sistema que no lo cumple es **peor** que no
tener documento. Sin documento hay una omisión; con documento incumplido hay una
prueba escrita, redactada por el propio comerciante, de lo que se comprometió a
hacer y no hizo. En una actuación de la SIC eso no se discute: se lee.

Esta skill trabaja en la dirección contraria a la de redacción. No pregunta "¿qué
debería decir el documento?" sino **"¿qué hace el sistema cuando alguien ejerce
lo que el documento le promete?"**. La respuesta suele ser una de tres, y las
tres importan por motivos distintos:

1. **Lo hace, y coincide.** Se anota y se sigue; hace falta una prueba que falle
   si deja de coincidir.
2. **Lo hace, pero distinto.** El texto dice quince días calendario y el código
   calcula treinta días hábiles. Es el hallazgo más peligroso porque nadie lo ve:
   las dos partes funcionan, solo que no dicen lo mismo.
3. **No lo hace.** No hay caso de uso, ni estado, ni pantalla, ni columna. Se
   atiende a mano, o no se atiende.

El tercero es el que la gente espera encontrar. El segundo es el que hunde casos.

## Bug, promesa incumplible y hueco de la ley

Son tres cosas distintas y mezclarlas arruina el informe.

- **Bug**: el sistema intenta cumplir y falla. Se arregla como cualquier defecto.
- **Promesa que la operación todavía no puede cumplir**: el código está bien, el
  negocio no tiene el proceso montado. No se arregla programando; se decide.
  Ejemplo real de este proyecto: los documentos prometen retracto, garantía y
  reversión del pago, y no existe ningún flujo que devuelva dinero. Hoy se
  atienden a mano, y eso puede ser una respuesta válida **si alguien lo sabe y lo
  decidió**. Lo intolerable es que nadie lo sepa.
- **Hueco de la ley**: la obligación existe aunque ningún documento la mencione.
  Callar no exime. Ver `references/obligaciones-sin-texto.md`.

Clasifica cada hallazgo en una de las tres. Un informe donde todo es "bug"
esconde decisiones de negocio; uno donde todo es "decisión pendiente" esconde
defectos.

## Lo que esta skill sí es y lo que no

Produce **hallazgos verificados y los cambios de código que los cierran**. No
produce asesoría jurídica ni texto legal.

- **Cuando el arreglo es cambiar el texto, no lo escribas aquí.** Pasa a
  `textos-legales-comerciales`, que impone la verificación de vigencia de la
  norma. Un plazo redactado a mano en mitad de una auditoría es exactamente el
  error que las dos skills existen para evitar.
- **Cuando el arreglo exige un dato del negocio que no tienes** —el horario de
  atención, el plazo de entrega que la operación puede sostener— **no lo
  inventes**. Se marca `[[ ]]`, se ajusta el documento para no publicar el
  marcador, y se pregunta. Un plazo inventado en el código es peor que uno
  inventado en un párrafo: se ejecuta.
- **Antes de preguntar, clasifica el dato que falta.** La mitad de los marcadores
  no son datos del negocio: unos están en la ley o en un calendario oficial
  —quién paga el flete de la devolución, los festivos—, otros ya se decidieron en
  un ADR y el texto no se enteró. Preguntarle al negocio un dato normativo no lo
  retrasa: lo congela, porque nadie va a decidir un festivo. El triaje está en
  `references/cerrar-marcadores.md`.
- **Cierra siempre recomendando revisión de un abogado colegiado** para los
  hallazgos que impliquen una decisión de riesgo. Una vez, al final.

---

## El flujo: verificar → inventariar → rastrear → contrastar → clasificar → cerrar

### Fase 0 — Verifica la obligación antes de auditarla

**Va primero y no es opcional.** Auditar contra una norma derogada produce
hallazgos falsos, y un hallazgo falso quema la credibilidad de todos los demás.

La Ley 2439 de 2024 tocó el comercio electrónico y los plazos del retracto hace
poco. Antes de declarar que un plazo del sistema está mal, **confirma cuál es el
plazo vigente hoy**, en fuente oficial.

Reglas duras, las mismas que la skill de redacción:

- **Nunca inventes un número de artículo, ley o decreto.** Sin confirmar, escribe
  la obligación sin la cita.
- **Nunca audites contra el RGPD.** El régimen colombiano tiene sus propios
  conceptos y plazos. Un informe que exige "derecho al olvido" o "interés
  legítimo" delata que se auditó con el manual equivocado.
- Si una norma cambió respecto del material de referencia, **dilo en la entrega**.

El mapa de qué norma regula qué, con fuentes oficiales, está en
`references/marco-normativo.md` de la skill `textos-legales-comerciales`. No lo
dupliques: léelo de allí.

---

### Fase 1 — Inventario de promesas: qué dice el sitio hoy

No auditas contra tu idea de lo que el sitio debería prometer. Auditas contra lo
que **de verdad dice**, palabra por palabra.

Recoge de los documentos publicados toda afirmación que comprometa una conducta
del sistema o del negocio. Sirve una regla simple: **si tiene un número, un
plazo, un canal o un verbo en futuro, es una promesa.**

Lo que hay que sacar:

| Tipo | Ejemplos de lo que se extrae |
|---|---|
| Plazos | retracto, reintegro del dinero, entrega, respuesta a consultas y reclamos, conservación de datos |
| Derechos ejercitables | conocer, actualizar, rectificar, suprimir, revocar la autorización, retracto, reversión |
| Canales | correo de PQR, teléfono, WhatsApp, formulario, horario de atención |
| Terceros | pasarela, transportadora o plataforma logística, correo transaccional, nube, analítica |
| **Costos y cargos** | qué incluye el precio, costo de envío, "sin costo", "envío gratis", quién paga el flete de la devolución, cargos que aparecen después |
| **Entrega y seguimiento** | plazo prometido frente a plazo estimado, cobertura, cómo se consulta el envío, qué se le muestra al comprador |
| Condiciones y exclusiones | qué productos no admiten retracto, qué anula la garantía |
| Identificación | razón social, NIT, dirección, datos del vendedor visibles |

**Una frase sobre qué incluye el precio es una promesa de las caras.** "El precio
incluye el envío", "no hay cobros adicionales al final del proceso" y "sin costo"
se rastrean hasta el cálculo del total, no hasta otra pantalla que las repita. Y
cuando el negocio cambia de modelo de cobro, esas frases son lo primero que hay
que releer: se vuelven falsas sin que nadie las edite.

**Una promesa que el texto hace en un idioma y no en el otro es un hallazgo**, no
una errata de traducción: son dos documentos y los dos se leen.

**Y una promesa que está en el archivo de textos pero que ninguna pantalla pinta
no existe.** Una clave traducida no es una clave visible. Este proyecto ya tuvo
el caso: la nota de que la versión en castellano es la que rige estaba escrita en
inglés, correcta, y ninguna plantilla la mostraba — o sea que jurídicamente no
estaba. **Comprueba que cada texto legal se renderice**, no que exista.

---

### Fase 2 — Rastrea cada promesa hasta el código

Por cada promesa del inventario, busca el artefacto que la ejecuta y **cita el
archivo y la línea**. Un hallazgo sin ruta y línea es una opinión.

Qué buscar según el tipo de promesa está en
`references/promesas-y-su-rastro.md`, que trae, para cada promesa típica de una
tienda colombiana, qué tiene que existir en el sistema: qué estado, qué columna,
qué endpoint, qué pantalla, qué tarea programada y qué evidencia.

El rastro completo de una promesa que se ejerce tiene cinco eslabones, y falta
uno con una frecuencia sospechosa:

1. **Puerta de entrada**: la pantalla o el canal por donde se pide.
2. **Caso de uso**: la operación de aplicación que lo ejecuta.
3. **Estado o dato**: dónde queda registrado que ocurrió.
4. **Efecto real**: el dinero que vuelve, el dato que se borra, el correo que
   sale.
5. **Evidencia**: qué queda para probar, después, que se cumplió y cuándo.

El eslabón 5 es el que casi siempre falta, y es el único que sirve el día de la
reclamación. Un reembolso que se hizo pero no dejó rastro es, para efectos
probatorios, un reembolso que no se hizo.

**Rastrea siempre hasta el dato, no hasta la pantalla.** Que exista un botón
"solicitar devolución" no prueba nada si no se puede seguir hasta la fila que
queda escrita.

#### Dónde mirar en este repositorio

- Textos publicados: `apps/web/src/assets/i18n/scopes/legales/{es,en}.json`, y su
  plantilla `apps/web/src/app/features/legales/presentation/documento/`.
- Casillas y consentimientos: `features/cuenta` y `features/checkout`.
- Constancia de autorización: `domain/legal`, tabla `autorizacion_datos`.
- Grafo de estados del pedido: `domain/pedido` — si un estado no está en el enum,
  el flujo no existe.
- Dinero y pagos: `domain/pago`, `infrastructure/pago`, y las tareas programadas.
- **Totales y cargos: `domain/pedido/Pedido.total()`.** Es el punto exacto donde
  se comprueba qué se cobra de verdad, y su comentario suele documentar la
  premisa vieja.
- **Lo que ve el comprador antes de pagar:
  `features/checkout/presentation/resumen/`** y las claves del scope `checkout`.
  Si ahí no hay una línea de envío y una de total, el resumen del pedido no
  cumple, por más completos que sean los documentos legales.
- **Envío, cotización y seguimiento: `domain/envio`, el cotizador en
  `infrastructure`, y el DTO público de seguimiento.** Ese DTO es donde se filtra
  el margen: ábrelo, no supongas que alguien lo recortó.
- Plazos configurables: `.env.example` y `application.yml`; un plazo legal
  incrustado en el código y no en configuración es un hallazgo por sí mismo.
- Migraciones: `infrastructure/src/main/resources/db/migration` — la columna que
  no existe delata el flujo que no existe.

---

### Fase 3 — Contrasta también contra lo que el documento no dice

Un sitio puede tener textos impecables y aun así incumplir, porque hay
obligaciones que no dependen de haberlas escrito: reversión del pago, canal de
reclamos exigible, información del vendedor visible antes de comprar, atención de
consultas y reclamos en plazo, tratamiento de datos de menores.

`references/obligaciones-sin-texto.md` trae la lista con lo que cada una exige
del sistema. Recórrela entera aunque el documento no las mencione: **el silencio
del documento no es una defensa, es un agravante**, porque también incumple el
deber de información.

---

### Fase 4 — Clasifica por riesgo, no por esfuerzo

Ordena los hallazgos por lo que pasa si nadie los toca, no por lo que cuesta
arreglarlos. El criterio, de mayor a menor:

1. **Bloquea el lanzamiento.** Incumplimiento que un comprador puede provocar el
   primer día y que no tiene salida manual razonable.
2. **Se atiende a mano y hay que decirlo.** Funciona con intervención humana; el
   riesgo es de volumen y de olvido. Exige que alguien lo sepa y lo acepte por
   escrito.
3. **Incoherencia latente.** El texto y el código no coinciden pero nadie lo ha
   ejercido todavía.
4. **Deuda de evidencia.** Se cumple pero no queda rastro.

Un hallazgo del nivel 1 que se arregla en diez minutos va antes que uno del nivel
3 que toma una semana. La ordenación por esfuerzo es la que deja abierto lo
grave porque era grande.

---

### Fase 5 — Cierra los huecos, de a uno

- **Un hueco por cambio.** No mezcles el retracto con la garantía porque los dos
  tocan el pedido: son dos decisiones y dos revisiones.
- **Cerrar de a uno no es modelar de a uno.** Es la otra cara de la regla
  anterior y se olvida siempre: los caminos legales se cierran por separado, pero
  lo que comparten —la constancia del dinero devuelto, el registro de una
  solicitud radicada por fuera— se modela una vez. El inventario de caminos de
  `promesas-y-su-rastro.md` va **antes** del primer commit, no después del
  tercero.
- **Cada hueco cerrado deja una prueba que falla si se reabre.** Es la regla dura
  #8 del proyecto y aquí tiene una vuelta de tuerca: la prueba tiene que
  comprobar **la promesa**, no la implementación. "El caso de uso devuelve
  `Reembolsado`" es débil; "un pedido entregado hace tres días admite retracto y
  uno de hace seis no" es la promesa.
- **Si la promesa lleva un plazo, el plazo va en configuración y la prueba lo
  fija.** Un número legal disperso en tres archivos se desincroniza el día que
  cambie la ley — y cambia.
- **Si el arreglo es cambiar el texto**, cambia el texto con la otra skill y
  revisa qué código arrastra: una versión, una fecha de vigencia, una prueba.
- **No cierres un hueco inventando el dato que le falta.** Márcalo `[[ ]]`,
  pregunta, y deja el resto listo — con el documento ajustado, porque **el
  marcador no se publica**. Ver `references/cerrar-marcadores.md`.

---

### Fase 5b — Cierra los marcadores que dejaron las auditorías anteriores

Puede ser el trabajo entero: "cierra los datos que quedaron pendientes" es una
tarea que llega sola, sin una auditoría nueva detrás. Y es la única fase que se
recorre **hacia atrás**, sobre los `[[ ]]` que esta misma skill sembró.

Importa porque un marcador no dispara nada. No rompe una prueba, no sale en un
informe y no lo mira nadie — hasta que alguien lee el documento publicado y lo
encuentra ahí, en mitad de una frase que promete un plazo.

Tres reglas, y el detalle en `references/cerrar-marcadores.md`:

1. **Un marcador no se publica nunca.** Es una anotación para quien audita, no un
   texto para quien lee. Compruébalo en la pantalla renderizada y no en el
   archivo de textos: la plantilla suele pintar la clave tal como está, y ese es
   justo el motivo por el que los marcadores llegan a producción.
2. **Clasifica antes de preguntar.** Dato normativo público / ya decidido en el
   repositorio / con respuesta en la norma / de verdad del negocio. Solo la
   cuarta clase se pregunta.
3. **Mientras el dato no llegue, dos salidas y solo dos:** quitar la promesa
   concreta, o declarar el mínimo legal diciendo que es el legal. Inventar un
   valor plausible no es la tercera: es convertir un dato faltante en un
   incumplimiento con prueba escrita.

Y cerrar un marcador nunca es cambiar un texto: arrastra el otro idioma, la
versión y la vigencia del documento, la versión guardada en cada constancia de
autorización, y el código que respondía "no se sabe" mientras el dato faltaba —
con su prueba, que cambia de sentido y no solo de valor.

---

### Fase 6 — Entrega

Usa `assets/plantilla-inventario-vacios.md`. Lleva cinco bloques:

1. **Resumen de si se puede abrir o no**, en una frase y sin rodeos.
2. **Tabla de promesas rastreadas**: promesa, dónde se promete, dónde se cumple,
   veredicto.
3. **Hallazgos**, ordenados por riesgo, cada uno con ruta y línea, clasificado en
   bug / decisión de negocio / hueco de la ley.
4. **Datos del negocio que faltan**, marcados `[[ ]]`.
5. **Puntos para revisión de abogado**: las decisiones de riesgo, con la
   disyuntiva explicada y sin resolverla.

Lo que **no** va en la entrega: hallazgos sin verificar contra el código.
"Probablemente no exista el flujo de garantía" no es un hallazgo. Ábrelo, míralo,
y escribe lo que hay.

---

## Errores que arruinan la auditoría

- **Auditar el texto contra la ley y olvidar el código.** Eso ya lo hace la otra
  skill. El valor de esta está en el rastro hasta el dato.
- **Dar por bueno lo que existe sin ejecutarlo.** Un endpoint que existe puede
  estar desconectado de la pantalla, o exigir un rol que el comprador no tiene.
- **Confundir "no hay pantalla" con "no se cumple".** Atender por correo es una
  forma válida de cumplir muchas obligaciones; lo que no es válido es no saber
  que se está haciendo así.
- **Dar por bueno un texto de costos porque hoy coincide.** "El precio incluye el
  envío" coincide con un sistema que suma solo las líneas — hasta el día que el
  negocio decide cobrar flete aparte, y entonces la frase queda falsa sin que
  nadie la edite. Cuando cambia el modelo de cobro, de entrega o de proveedor
  logístico, **el inventario se rehace**: no es una auditoría nueva, es la misma
  con una premisa distinta.
- **Auditar solo la pantalla que ya existe.** Una pantalla que hoy cumple porque
  dos cifras coinciden —subtotal y total— deja de cumplir cuando dejan de
  coincidir. Pregunta qué cifras van a existir después del cambio, no solo qué
  cifras hay.
- **Reportar el volumen en vez del riesgo.** Cuarenta hallazgos triviales entierran
  los tres que importan.
- **Un hallazgo sin ruta y línea.** Es una sospecha, y las sospechas no se
  entregan como hallazgos.
- **Arreglar el texto para que coincida con el código.** A veces es lo correcto,
  pero es la salida fácil y hay que justificarla: si el código incumple la ley,
  ajustar el documento a lo que el código hace consagra el incumplimiento por
  escrito.
- **Cerrar un hueco sin prueba.** Vuelve solo, y la próxima vez nadie lo mira
  porque "eso ya se había arreglado".
- **Dejar el marcador a la vista.** Marcar el hueco es correcto; publicarlo es un
  incumplimiento del deber de información por sí mismo, y encima deja por escrito
  que el comerciante sabía que le faltaba el dato. Se comprueba en la pantalla,
  no en el archivo de textos.
- **Tratar un dato normativo público como dato del negocio.** Los festivos, los
  términos supletivos y el reparto legal de costos no los decide una tienda. Un
  marcador mal clasificado no espera: se queda para siempre, y con él todo lo que
  cuelgue de él.
- **Auditar una sola vez.** El inventario se rehace cuando cambia un texto legal
  o cuando entra un flujo que toca dinero, datos o entrega.

## Índice de recursos

**Guías** (`references/`)
- `promesas-y-su-rastro.md` — para cada promesa típica de una tienda colombiana,
  qué tiene que existir en el sistema para que sea verdad
- `obligaciones-sin-texto.md` — lo que la ley exige aunque ningún documento lo
  mencione, y qué exige del código
- `cerrar-marcadores.md` — la operación inversa: cómo se cierra un `[[ ]]` que ya
  existe, por qué no se publica nunca, y qué arrastra cerrarlo

De la skill hermana `textos-legales-comerciales`:
- `references/marco-normativo.md` — mapa de normas y fuentes oficiales para
  verificar vigencia. No se duplica aquí.

**Plantillas** (`assets/`)
- `plantilla-inventario-vacios.md` — el documento de entrega
