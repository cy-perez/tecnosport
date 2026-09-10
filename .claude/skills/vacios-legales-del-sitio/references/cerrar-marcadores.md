# Cerrar un marcador `[[ ]]`

La skill trabaja casi toda en una dirección: encontrar el hueco y marcarlo. Esta
referencia hace la operación inversa, que es la que nadie planea — **tomar un
marcador que ya existe y cerrarlo** — y existe porque un marcador es una deuda
sin fecha de vencimiento: no rompe ninguna prueba, no aparece en ningún informe,
y sobrevive fases enteras hasta que alguien lee el documento publicado.

---

## Regla dura: un marcador no se publica nunca

El marcador es una anotación para quien audita y para quien programa. **Para
quien lee el documento no es nada**, y ahí está el daño: un documento que dice
"el plazo de entrega es de `[[PLAZO DE ENTREGA REAL]]` días calendario" informa
peor que uno que no prometa plazo, y además deja por escrito, de puño del propio
comerciante, que sabía que le faltaba el dato y publicó igual.

Este proyecto lo tuvo seis veces a la vez, durante una fase completa, y el motivo
es tan simple que conviene no olvidarlo: la plantilla del documento legal pinta
la clave de traducción tal como está. Nadie escribió un filtro porque nadie
pensó que hubiera que filtrar.

Dos consecuencias operativas:

- **Marcar y publicar son incompatibles.** Si el hueco se marca, el documento se
  ajusta en el mismo cambio con una de las dos salidas provisionales de más
  abajo.
- **La regla necesita una guarda**, no buena memoria. En este repositorio es
  `tools/verificar-marcadores.mjs`, enganchada en `npm run verificar` junto a las
  capas y los contrastes. Una guarda que hay que acordarse de correr no es una
  guarda.

---

## Las cuatro clases: clasifica antes de preguntar

Preguntarle al negocio es el último recurso, no el primero. Tres de cada cuatro
marcadores se cierran sin molestar a nadie.

### 1. Dato normativo público

Está en una ley, en un decreto o en un calendario oficial. **No se pregunta: se
verifica en fuente oficial y se carga.** Los festivos colombianos son el ejemplo
puro — dependen de la Ley 51 de 1983 y de la Pascua, no de lo que decida una
tienda.

La señal para reconocerlo: **si la respuesta es la misma para cualquier
comerciante del país, no es un dato del negocio.**

Confundirlo con uno de negocio no lo retrasa: lo congela. Nadie va a "decidir" un
festivo, así que el marcador se queda ahí para siempre, y con él todo lo que
cuelgue de él. Aquí eso fueron tres plazos legales respondiendo "no se sabe"
mientras el dato estaba publicado en el Diario Oficial.

### 2. Ya decidido en el repositorio, y el texto no se enteró

Hay un ADR, una variable de entorno o un documento de infraestructura que
responde el marcador. Se cierra leyendo el repositorio. La señal: el marcador es
**más viejo** que la decisión.

Con una trampa que hay que mirar de frente: **decidido no es construido.**
Nombrar en la política de datos al tercero que todavía no recibe ni un dato es
cambiar una promesa falsa por otra igual de falsa, solo que más concreta y por
eso más fácil de desmentir. Mientras el tercero no reciba nada, se describe la
categoría; se lo nombra el día que lo reciba, y ese día es parte del trabajo de
integrarlo.

### 3. Con respuesta en la norma, aunque parezca del negocio

"Quién paga el flete de la devolución" suena a decisión comercial y no lo es: la
ley reparte esos costos y el reparto no se negocia en un párrafo. Antes de
preguntar, busca la obligación en `obligaciones-sin-texto.md` y verifica su
vigencia en `marco-normativo.md` de la skill hermana.

Y ojo con dar por hecho que la respuesta es una sola: **un mismo marcador puede
necesitar repartos distintos según el camino legal.** El transporte de una
devolución por retracto y el de una reclamación de garantía no los paga el mismo.
Un marcador que se cierra con dos frases donde el texto tenía una no es un
marcador mal escrito: es un hallazgo.

### 4. De verdad del negocio

El horario de atención, el plazo de entrega que la operación puede sostener, el
proveedor que todavía no se ha contratado. **Solo aquí se pregunta**, y la
pregunta se anota con qué la desbloquea. Aun así el documento no se queda con el
marcador: sigue la sección siguiente.

---

## Mientras el dato no llegue: dos salidas, y solo dos

**a. Quitar la promesa concreta.** Se borra la cifra y queda la afirmación
genérica que sí es verdad y sí es verificable. "Respondemos toda petición en un
máximo de quince días hábiles" se sostiene sin declarar un horario; el horario se
agrega cuando exista.

**b. Declarar el mínimo legal, diciendo que es el legal.** Cuando la ley trae un
término supletivo, publicarlo es correcto y además honesto: obliga a lo mismo a
lo que ya obliga la ley, y deja claro que no hay plazo pactado. Lo que no se
puede hacer es publicarlo como si fuera el plazo real de la operación.

**Lo que no es una salida: inventar un valor plausible.** "De 3 a 5 días
hábiles" parece inofensivo y es lo peor de las tres opciones — lo anunciado se
vuelve exigible, y una cifra que nadie midió convierte un dato faltante en un
incumplimiento con prueba escrita. El texto provisional se redacta con
`textos-legales-comerciales`, no a mano en mitad de la auditoría.

---

## Lo que arrastra cerrar un marcador

Cerrar un marcador nunca es cambiar un texto. Recorre esta lista entera:

1. **Los dos idiomas.** Son dos documentos y los dos se leen. Un marcador cerrado
   en castellano y vivo en inglés sigue publicado.
2. **La versión y la fecha de vigencia** del documento.
3. **La versión que el servidor guarda en cada constancia de autorización**, si el
   marcador vivía en la política de datos. Ahí hay que decidir algo que no es
   técnico: si la versión nueva se vuelve a pedir o si la vieja queda como
   constancia histórica de lo que esa persona aceptó ese día. Decidir "nada"
   también es decidir, pero hay que escribirlo.
4. **El código que respondía "no se sabe" mientras faltaba el dato.** Una lista de
   categorías sin término conocido, una rama `INDETERMINADO`, un veredicto que se
   negaba a afirmar. Con el dato cargado, ese camino cambia de significado o deja
   de ser alcanzable — y hay que mirar qué pantalla lo pintaba.
5. **La prueba de ese código**, que no cambia de valor sino de sentido: la que
   afirmaba "no se puede saber" pasa a afirmar la promesa.
6. **La guarda**, que ya no tiene que perdonar ese marcador.

---

## Prueba que lo sostiene

Dos, y son distintas:

- **La de la promesa nueva**, que falla si el sistema deja de cumplirla. Si la
  promesa quedó en el mínimo legal, la prueba fija el mínimo: el día que alguien
  "mejore" el texto sin tocar el código, falla.
- **La guarda del marcador**, que falla si se publica cualquier otro. Es la que
  impide que esta referencia haya que volver a escribirla.
