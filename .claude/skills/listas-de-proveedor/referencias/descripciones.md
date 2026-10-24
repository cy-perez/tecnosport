# Estructura de la descripción

Todas las descripciones usan la misma estructura. Un catálogo donde cada ficha
está escrita distinta se ve improvisado y obliga al cliente a buscar el dato en un
lugar diferente cada vez.

## Plantilla

```markdown
[Párrafo de apertura: 50 a 80 palabras. Qué es, para quién y el dato técnico que
lo distingue de su versión anterior o de su competencia directa.]

## Características principales
- [5 a 7 viñetas, cada una un beneficio concreto con su dato: pantalla, procesador,
  cámara, batería, conectividad, resistencia]

## Ficha técnica
| Atributo | Detalle |
|---|---|
| Pantalla | |
| Procesador | |
| Memoria | |
| Cámara | |
| Batería | |
| Conectividad | |
| Sistema operativo | |
| Dimensiones y peso | |

## Contenido de la caja
- [lo que trae realmente]

## Garantía y notas
- [garantía, condición del equipo, aclaraciones de eSIM o compatibilidad]
```

## Lo que la ficha del sitio sabe pintar

La ficha del producto no tiene una librería de Markdown detrás: la lee
`apps/web/src/app/shared/ui/descripcion/leer-descripcion.ts`, que reconoce
exactamente lo de la plantilla de arriba, y nada más:

| Se escribe | Se ve |
|---|---|
| un párrafo, separado por una línea en blanco | párrafo; sus saltos de línea se respetan |
| `## Subtítulo` | subtítulo de sección |
| `- viñeta` | lista con viñetas |
| `\| Atributo \| Detalle \|` + `\|---\|---\|` + filas | tabla de dos columnas, el atributo como encabezado de fila |
| una dirección `https://…` | enlace |
| `---` y lo que vaya debajo | **nada**: no se pinta (ver «Sin fuentes») |

Lo demás sale **tal cual, con la sintaxis a la vista**: `###` o `#`, negrita con
`**`, listas numeradas, citas con `>` y enlaces `[texto](dirección)`.
`descripciones.py` lo avisa como «usa un formato que la ficha del sitio no
pinta». Y **ninguna barra `|` dentro de un valor de la tabla**: abre otra columna.
`redactar_fichas.py` la cambia por una coma («GPS, Galileo, Glonass») y
`descripciones.py` avisa si una descripción heredada todavía la trae.

Los atributos de la ficha cambian por categoría: un parlante lleva potencia,
autonomía, resistencia al agua y versión de Bluetooth; un portátil lleva
pantalla, procesador, RAM, almacenamiento, gráficos, puertos y peso.

## Metadatos

- **Meta título**: máximo 60 caracteres, empieza por la marca y el modelo.
- **Meta descripción**: máximo 155 caracteres, dice qué es y un diferenciador.
  Sin "compra ya" ni signos de admiración.

## Tono

Informativo y verificable. El cliente que compra un celular de dos millones ya
comparó en tres sitios: lo que convence es el dato exacto, no el adjetivo.

- Nada de "el mejor", "increíble", "revolucionario".
- Cada afirmación técnica sale de la ficha oficial del fabricante. Si no se pudo
  confirmar, no entra.
- No prometer autonomía en horas, cobertura ni velocidades que el fabricante no
  declare.
- Español neutro, medidas en el sistema métrico, precios en pesos.

## La descripción es del modelo, no de una memoria

Vale para todas las configuraciones del modelo: no dice «Esta versión trae 6 GB de
RAM y 256 GB de almacenamiento» ni «el Galaxy A57 de 256 GB». La memoria y el color
son variantes que el cliente elige. `descripciones.py` lo avisa siempre como
«nombra una configuración»: el 22/10/2026 el Redmi 17 decía 6 GB también en su
versión de 4 GB. La RAM sola no cuenta cuando es la misma en todas las memorias
(el Moto G15 trae 4 GB en las tres), y la tabla de la ficha sí puede nombrar
capacidades («microSD de hasta 1 TB»).

## Declaraciones que no se pueden omitir

- **Equipos activados**: decir que la garantía del fabricante ya está corriendo y
  desde cuándo, si se sabe.
- **Accesorios compatibles**: si el accesorio no es de la marca del equipo con el
  que se usa, la ficha dice "compatible con", nunca la marca a secas.
- **eSIM**: si el equipo no tiene bandeja física, decirlo en la apertura, no en la
  ficha. Es el reclamo más común en equipos importados.
- **RAM extendida**: si la lista trae `(8+8+256)`, la ficha dice 8 GB de RAM y
  menciona aparte los 8 GB de RAM virtual que el equipo toma del almacenamiento.
  Sumarlas es una afirmación falsa que el cliente verifica en dos toques.
- **Garantía**: quién responde y por cuánto tiempo. En Colombia la garantía legal
  aplica aunque no se mencione; lo que evita el reclamo es que esté escrita.

## Sin fuentes: la descripción no dice de dónde salió un dato

**Decisión del negocio del 23/10/2026.** La descripción que se publica no nombra
la fuente consultada: ni un pie de fuentes tras `---`, ni frases como «La tabla
técnica sale de Open Icecat…», «según la ficha de JBL», «la ficha de Xiaomi
Colombia indica…», «según la página oficial» o «según el proveedor». El dato se
escribe solo: «Este modelo no tiene NFC», no «La ficha de Xiaomi indica que este
modelo no tiene NFC». Y la nota que solo explicaba de dónde salió algo —«la ficha
del fabricante no declara X, así que no se promete»— no se escribe: lo que no se
declara basta con no prometerlo.

- **La fuente no se pierde**: va en `fuentes_ficha` del modelo en
  `conocidos.json` —la de la ficha y, con `"seccion": "caja"`, la de la caja—,
  y la ficha interna de la carpeta del modelo la lista. Si un cliente reclama un
  dato, de ahí se sabe de dónde salió.
- **Lo hace el código**: `redactar_fichas.py` ya no escribe el pie;
  `descripciones.sanear` quita el pie y las viñetas que nombran Icecat de una
  descripción heredada; y `descripciones.defectos` avisa cualquier otra frase
  que cite su fuente («nombra de dónde salió un dato») y **deja al modelo fuera
  de la exportación** hasta que se reescriba. El sitio, además, no pinta el pie
  aunque llegue.
- **«según Apple» o «según el fabricante» sí se quedan**: no dicen qué página
  se consultó sino quién declara la cifra, y en la autonomía eso es lo que evita
  prometer horas que la tienda no puede garantizar (ver «Tono»).
- **Incluye a Open Icecat, y es un riesgo asumido.** Su Open Content License
  (cláusulas 1 y 2) pide el aviso de derechos, el descargo de garantía y la nota
  de lo que se modificó junto a los datos. Publicar una tabla de Icecat sin ese
  aviso incumple la licencia; el negocio lo decidió así sabiéndolo, y la
  pregunta quedó en el numeral 8 de `docs/14-consultas-al-abogado.md`. Si la
  respuesta es que no se puede, la salida es dejar de usar Icecat para las
  tablas, no volver a publicar el pie.

Para los textos legales del sitio (términos, garantías, retracto, datos
personales) usa la skill `textos-legales-comerciales`, no los redactes aquí.
