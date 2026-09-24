# ADR-0060 — La marca corta y la interfaz redondea

**Fecha:** 2026-09-24
**Estado:** aceptado. Deroga la parte de "radio 0 en todo" que aplicaba a la
interfaz; el chaflán a 45° sigue intacto como firma de la marca.

## Contexto

El manual de marca no declaraba "radio 0" como una preferencia de estilo: lo
declaraba como la idea central del sistema. «No hay una sola curva ni una sola
esquina redondeada en todo el sistema. Esa disciplina es lo que lo hace
reconocible.» De ahí salía el chaflán a 45° en las dos esquinas opuestas —el
corte del isotipo— aplicado a botones, tarjetas de producto, etiquetas de
precio y recortes de fotografía, y de ahí salían los cuatro `radio_px` en 0 de
`tokens.json`, declarados solo para que ningún componente quedara con una
variable sin definir.

El 24 de septiembre de 2026 los dos alternadores del encabezado —tema e
idioma— se rehicieron tomando como referente
`https://angular-demo.tailadmin.com`. Se plasmaron cuadrados, porque la regla
mandaba, y el comentario del botón de tema lo dejó escrito: «el botón de
referencia era un círculo; aquí manda radio 0 en todo». El mismo día se pidió lo
contrario: que se vieran como en la plantilla de referencia, y que el sistema
admitiera bordes redondeados porque vienen más cambios de este tipo.

## Decisión

**El chaflán a 45° se reserva a la marca. La interfaz del sitio redondea.**

- **Chaflán:** logo, isotipo, piezas gráficas, portadas de redes, papelería,
  empaque, impresos, recortes de fotografía. Sigue resuelto en la clase
  `.chaflan` de `tokens.css`, con `--ch` para el tamaño. No cambia nada de lo
  que hace, solo dónde se usa.
- **Radio:** todo lo que es interfaz — controles, superficies, contenedores.
  Sale de una escala del kit, no de la de Tailwind:

  | Token | Valor | Dónde |
  |---|---|---|
  | `--radio-sm` | 6 px | El segmento de dentro de un grupo, la insignia, la etiqueta pequeña |
  | `--radio-md` | 8 px | El control suelto, que es el caso común: botón, campo, alternador, menú |
  | `--radio-lg` | 12 px | La superficie que contiene controles: tarjeta, panel, diálogo |
  | `--radio-completo` | 9999 px | La píldora y el botón circular |

- **Un elemento lleva una cosa o la otra, nunca las dos.**

Los valores salen medidos de la plantilla de referencia, no inventados: su
botón de tema es un círculo de 44 px con borde de 1 px, su control segmentado
es una pista de 8 px con la pastilla del activo en 6 px.

`--radio-completo` son 9999 px y no un valor exacto a propósito: un radio mayor
que la mitad del lado corto redondea hasta el semicírculo y ahí se detiene, así
que el mismo token hace circular un botón de 44 px y pastilla una insignia de
20 sin que nadie calcule la mitad.

## Cómo llega a las plantillas

En `apps/web/src/tailwind.css`, dentro del `@theme inline`:

```css
--radius-sm: var(--radio-sm);
--radius-md: var(--radio-md);
--radius-lg: var(--radio-lg);
--radius-completo: var(--radio-completo);
```

El borrado de `--radius-*: initial` de `ADR-0020` se queda, y ahora cuenta otra
historia: no borra los radios para que no existan, los borra para que los que
existan salgan del kit. `rounded-md` es un token; `rounded-xl` sigue sin
existir.

**No hay colisión de nombres** como la que obligó al prefijo `ts-` en los
colores: el token es `--radio-md` y lo que Tailwind lee es `--radius-md`.

**`cn()` necesitaba enterarse.** `tailwind-merge` conoce `sm`, `md` y `lg` de su
escala por omisión, pero no `completo`, así que `cn('rounded-md',
'rounded-completo')` habría dejado las dos clases y el ganador lo habría
decidido el orden del CSS compilado. Se registra como escala de tema
(`extend.theme.radius`) y no como grupo de clases, porque así las variantes por
esquina —`rounded-t-completo`, `rounded-bl-completo`— quedan resueltas solas en
vez de haber que enumerarlas.

## Lo que cuesta

**La marca pierde una regla que era fácil de verificar y de explicar.** "Radio
0 en todo" se auditaba con un `grep` y no admitía discusión; "el chaflán es
marca y el radio es interfaz" exige juzgar de qué lado cae cada pieza, y va a
haber casos ambiguos — una etiqueta de oferta sobre una foto de producto, por
ejemplo. La frontera está escrita arriba y en `docs/04-ui-marca.md`; cuando no
resuelva un caso, se decide y se escribe, no se improvisa dos veces distinto.

**Y quedan dos geometrías vivas a la vez durante un tiempo.** El cambio entró
por los dos alternadores del encabezado. Siguen con `.chaflan`: `ts-boton`,
`ts-tarjeta-producto`, `ts-dialogo`, el hero de la portada y el enlace de salto
al contenido. Se migran cuando se toquen, no de una sentada: un barrido de todas
las superficies del sitio en un solo commit es justo el cambio que nadie puede
revisar.

## Alternativas descartadas

- **Dejarlo como estaba.** Descartada por petición explícita, con la
  contrapartida de arriba delante.
- **Redondear solo estos dos controles, sin frontera declarada.** Habría
  evitado el compromiso de hoy y comprado una decisión pendiente en cada
  pantalla futura. La misma pregunta contestada quince veces son quince
  respuestas distintas.
- **Copiar también el relleno de 2 px de la pista del control segmentado.** Es
  lo único de la referencia que no se copió, y no por gusto: allá la pista mide
  40 px y los segmentos 36, mientras que aquí la pista mide los 44 del objetivo
  táctil de la tabla de medidas. Ese relleno saldría de los segmentos, que
  bajarían a 36 px de alto —por debajo del mínimo del proyecto—, o empujaría el
  grupo a 52 px y lo descuadraría con el botón circular del tema que va justo al
  lado. Con relleno 0 y el mismo radio en pista y pastilla, las esquinas encajan
  sin dejar un filo de pista a la vista.
