# Pruebas

No hay TDD estricto en este proyecto, pero nada se considera terminado sin una
prueba que falle si la lógica se rompe. Una prueba que pasa con la implementación
borrada no es una prueba.

## Cómo se comprueba que una prueba prueba algo

Verla pasar no dice nada: dice que pasa. **Una prueba nueva se comprueba
mutando la implementación que dice cubrir** —invertir la comparación, quitar el
guardia, cambiar la constante— y verificando que falla, y que falla *la que le
toca*. Después se deshace la mutación. Es la práctica con la que se construyó la
Fase 5 entera y encontró cosas que ninguna corrida verde encontró:

- Una prueba del visor 360 que forzaba una revalidación y comprobaba que el
  fotograma no se movía **pasaba con el defecto puesto**: la revalidación no
  estaba llegando al componente y la prueba no lo comprobaba. Sin la mutación
  habría quedado un defecto vivo marcado como inexistente.
- Una prueba del valor por omisión de `ts-galeria.prioritaria` pasaba igual con
  el defecto cambiado a `true`, porque el ayudante de la prueba mandaba siempre
  el input y nadie ejercitaba el valor por omisión.
- `TokensJwtTest` fallaba una de cada dieciséis corridas desde siempre: cambiaba
  el último carácter del token, que en una firma HS256 aporta cuatro bits, así
  que el token "manipulado" a veces era byte por byte el mismo.
- **`vi.spyOn(window.localStorage, ...)` no sustituye nada en jsdom**: su
  `localStorage` es un Proxy y el espía no llega a aplicarse. Una prueba del
  camino degradado del visor 360 —qué pasa cuando el almacén lanza— pasaba
  idéntica con y sin el `try/catch` que decía cubrir, y solo se vio al mutar.
  Para sustituir el almacén completo va `vi.stubGlobal('localStorage', ...)`.
  Cuidado con el patrón general: **un doble que en realidad no se instala hace
  pasar la prueba por el camino normal**, que también pasa — es el peor tipo de
  prueba verde, porque parece cubrir justo lo que no cubre.
- **Y su variante, que costó cuatro fases:** un doble que sí se instala pero
  **falla distinto que la implementación real**. Los seis `EnviadorDeCorreoFalso`
  lanzaban `IllegalStateException`; el adaptador de producción no lanzaba nada,
  se tragaba el fallo. Así que las pruebas que afirmaban "un correo caído no deja
  la solicitud guardada a medias" comprobaban un escenario que producción no
  podía producir, y una de ellas afirmaba **lo contrario** de lo que el sistema
  hacía — en verde todo el tiempo. Un doble tiene que fallar con el mismo tipo y
  en los mismos casos que lo que imita; si no, lo que prueba es a sí mismo. Ver
  `adr/0044`.
- **Y la tercera variante, encontrada el 21 de septiembre: un doble que devuelve
  el mismo objeto que le sembraron.** Los once `RepositorioInventarioFalso`
  guardaban el agregado en un mapa y lo devolvían tal cual en cada lectura. El
  adaptador real no hace eso: reconstruye un `Inventario` nuevo desde sus filas,
  así que una mutación que no se guarde **se pierde**. Con los dobles viejos, la
  prueba y el caso de uso compartían el objeto, de modo que quitar el
  `repositorioInventario.guardar(inventario)` de `CrearPedido` dejaba la batería
  entera en verde — y en producción eso es una reserva que no existe, una línea
  de pedido con un `id_reserva` que no apunta a ningún movimiento, y sobreventa.

  La regla que se saca: **un doble de un repositorio copia en la lectura**. Si el
  real reconstruye el agregado —y cualquiera que mapee de filas a objetos lo
  hace—, devolver la instancia guardada convierte "olvidé persistir" en algo
  indistinguible de "persistí". Ver `adr/0054`.
- **Afirmar que algo NO está en pantalla no afirma nada si no se esperó a que la
  pantalla tuviera datos.** La primera versión de la prueba de "el aviso no se
  enciende con solo borradores" pasaba igual con el defecto puesto: esperaba al
  botón de cerrar sesión —que se pinta de inmediato— y preguntaba por el enlace
  con `queryByRole`, que no espera. Con la consulta todavía sin resolver, el
  aviso no estaba por el motivo equivocado. **Una aserción de ausencia necesita
  un ancla**: algo que solo se puede ver cuando los datos ya llegaron, esperado
  con `findBy*` antes de preguntar por lo que no debería estar.
- **Un `@defer` deja fuera del DOM lo que envuelve, y las pruebas que no lo
  nombran siguen verdes.** `TestBed` no dispara los bloques diferidos por
  omisión. Al meter el pie en un `@defer (on immediate; hydrate on viewport)`
  (22 de septiembre), las cinco pruebas de `app.spec.ts` siguieron pasando —
  incluida la de axe, cuyo comentario dice que cubre "encabezado, enlace de
  salto, landmark principal y pie" y que había dejado de ver el último. No es
  un fallo del `@defer`: es que **una prueba de ausencia implícita no existe**.
  Un bloque diferido nuevo obliga a dos cosas:
  `deferBlockBehavior: DeferBlockBehavior.Playthrough` en el `TestBed` —o en el
  `render` de Testing Library, que lo acepta igual— y una prueba que afirme que
  el contenido se pinta, comprobada cambiando el disparador normal por uno
  perezoso para verla caer.
- **Una prueba puede fijar un defecto tan bien como fija un acierto.**
  `unFalloAlEnviarSeRegistraYNoPropaga` exigía `doesNotThrowAnyException()` y lo
  que protegía era el error. La cobertura no distingue las dos cosas: al leer una
  prueba verde hay que preguntarse si lo que afirma es lo que se quiere, no solo
  si es lo que pasa.

Una prueba que no falla ante ninguna mutación razonable se borra o se arregla;
dejarla es peor que no tenerla, porque cubre el hueco en el informe de cobertura
y no en el código.

## Backend

| Capa | Herramienta | Qué se prueba |
|---|---|---|
| `domain` | JUnit 5 puro | Invariantes, transiciones de estado, totales, redondeo |
| `application` | JUnit 5 con puertos falsos | El caso de uso completo, incluidos los caminos de error |
| `infrastructure` | Testcontainers con PostgreSQL 16 | Consultas, migraciones, mapeadores, bloqueos |
| `presentation` | `@WebMvcTest` | Códigos HTTP, validación, formato de error |
| Arquitectura | ArchUnit | Las flechas de dependencia. Falla el build |
| Contrato | `ContratoOpenApiTest` | Que el OpenAPI servido sea `packages/contratos/openapi.json` |
| Extremo a extremo | Spring Boot Test con Wompi y Skydropx falsos | Los recorridos de `00-producto.md` |

Nunca H2. Si la prueba no corre contra el mismo motor que producción, no prueba
la consulta que importa.

**La prueba del contrato es una prueba de límite, no de comportamiento**, y por eso está en la
tabla: lo que rompe no es una regla de negocio, es lo que el frontend cree que va a recibir. El
21 de septiembre de 2026 el único guardián de eso era un trabajo de integración continua que
levantaba PostgreSQL y `bootRun` para regenerar el cliente y mirar el diff: correcto, pero
veinte minutos tarde y ya sobre la rama. Con el OpenAPI guardado en el repositorio, el mismo
guardián son dos eslabones que corren en la máquina de quien programa —esta prueba en `gradlew
build`, y `tools/verificar-contratos.mjs` en `npm run verificar`— y el trabajo de CI sobró.

**Y hay un hueco entre capas que solo una prueba de `infrastructure` ve: el mapeo JPA contra la
migración.** El 19 de septiembre de 2026, al hacer opcional el paquete de la variante
(`adr/0046`), la `V55` quitó el `not null` de las cuatro columnas y los campos de la entidad
pasaron a `Integer` — pero se quedaron sus `@Column(nullable = false)`. Resultado: **la base
aceptaba el nulo y Hibernate lo rechazaba antes de llegar a ella**, con un 500.

Las pruebas de dominio, de caso de uso y de controlador pasaban todas, y ninguna podía ver el
problema: las tres primeras no tocan JPA y la cuarta usa un doble. Lo destapó cargar producto real.

De ahí la regla: **cuando una migración cambia la nulabilidad, la longitud o el tipo de una
columna, la prueba que lo demuestra escribe y vuelve a leer esa fila contra Postgres.** No basta con
que la migración corra — corrió, y el defecto estaba del otro lado del mapeo. Y va con su pareja:
una prueba que guarde el caso *lleno*, para que la del caso vacío no pase por no leer nada.

Lo que tiene prueba sin excepción:

- Cálculo de total con IVA y redondeo.
- Reserva de inventario con dos compradores simultáneos por la última unidad.
- Vencimiento de reserva a los 30 minutos con `Reloj` falso.
- Reserva de contraentrega que **no** vence por tiempo.
- Disponibilidad de contraentrega: ninguna tarifa cotizada admite recaudo, sobre
  el monto máximo, y comprador con rechazo previo. Los tres deben excluir el
  método. La primera premisa cambió con `ADR-0023`: ya no es "no está en la tabla
  de cobertura".
- **Total del pedido con envío**: subtotal más flete, con el flete en cero cuando
  la entrega es recogida en el punto, y sin repartir el flete entre las líneas.
- **Cotización fallida o destino sin cobertura**: el pedido a domicilio no se
  crea, y la recogida en el punto sí. Nunca un flete de respaldo inventado.
- **El costo de envío que llega del cliente se ignora.** La prueba manda un
  `costoEnvio` manipulado en el cuerpo y comprueba que el total sale del servidor.
- **Tarifa vencida al despachar**: se cotiza de nuevo para la guía y el total del
  pedido **no** cambia.
- Webhook de Wompi repetido: no cobra ni descuenta dos veces.
- **Webhook de seguimiento repetido**: no escribe dos eventos ni transiciona dos
  veces; y solo `picked_up`, `delivered` e `in_return` mueven el pedido — los
  otros nueve estados se guardan sin tocar `Pedido.estado`.
- Webhook con firma inválida: se rechaza.
- Transición de estado inválida: se rechaza.
- Un pedido histórico no cambia de total cuando cambia el precio del catálogo.
- Un set de rotación incompleto no se publica.

## Frontend

Runner: Vitest. Angular 22 lo soporta a través de su builder de pruebas; si esa
configuración falla, la alternativa es el plugin de Analog. Verificar contra la
documentación de la versión antes de configurar, no de memoria.

| Qué | Cómo |
|---|---|
| Funciones puras y mapeadores | Vitest, sin Angular |
| Stores de signals | Vitest, sin TestBed |
| Componentes | Testing Library, por rol y texto accesible |
| Accesibilidad | `axe-core` en las pantallas clave, más `npm run contrastes` |
| Recorridos completos | Playwright, solo los cinco de `00-producto.md` |

Específico del 360, porque es lo que más se rompe en silencio:

- Recorte y escala: funciones puras sobre imágenes de prueba conocidas,
  verificando el rectángulo detectado y que el factor de escala sea **común a
  todo el set**.
- Mapeo de desplazamiento a índice: función pura, con vueltas circulares y
  valores negativos.
- Nivelador: función pura sobre lecturas simuladas, con datos ruidosos y con
  sensor ausente.
- Visor: teclado, botones, y que el arrastre vertical no gire ni bloquee el
  desplazamiento de la página.
- Asistente de captura: **la pantalla entera se prueba sin cámara**. Todo lo que
  toca el dispositivo entra por un puerto —`CAMARA`, `SENSOR_ORIENTACION`,
  `PANTALLA_DESPIERTA`, `ALMACEN_LOCAL_DE_CAPTURAS`, `PROCESADOR_DE_FOTOGRAMAS`—
  y la prueba inyecta un doble. No es ceremonia: es lo que obliga a que el
  camino degradado (sin sensor, sin disco) exista de verdad y no solo de
  palabra, porque una prueba lo puede simular.

**Lo que ninguna prueba de Vitest puede dar, y por eso se recorre a mano en un
teléfono real** (`apps/web/README.md` explica cómo, con el túnel HTTPS): el
gesto táctil, el permiso del sensor de orientación de iOS —que exige gesto del
usuario y certificado confiable—, el nivel con un pulso humano, y la rama de
conexión lenta / ahorro de datos, que no se puede simular desde la
automatización del navegador.

### Accesibilidad automatizada

`axe-core` directo, sin envoltorio: `vitest-axe` va por la 0.1.0 y `jest-axe` es
de otro runner, mientras que el motor no tiene peers y hace justo lo que hace
falta. El ayudante está en `src/testing/axe.ts` y se usa así:

```ts
const { container } = await renderCarrito(repositorio);
await screen.findByText('Morral urbano');
await esperarSinViolaciones(container);
```

Corre solo las reglas de WCAG 2.2 A y AA, y falla con la regla, el enlace a su
explicación y el HTML del nodo culpable.

**Cubierto hoy:** el cascarón de la aplicación —que está en todas las
pantallas— más rejilla, ficha, carrito y resumen del checkout. Se audita
**después de esperar a que la pantalla tenga datos**: auditar un esqueleto de
carga no prueba nada.

**Dos reglas van desactivadas a propósito, y no es esconder nada:** en jsdom no
hay maquetación, así que `color-contrast` y `target-size` no pueden evaluarse
—nada tiene tamaño ni posición—. Las dos están cubiertas mejor por otra vía: el
contraste con **`npm run contrastes`**, que calcula los pares reales de
`tokens.css` en los dos temas; y el objetivo táctil con `min-h-tactil`, que es
una clase verificable con `npm run clases` — que desde el 21 de septiembre de 2026, sin
argumentos, barre el frontend entero en vez de responder por una clase a la vez.

### El guion de NVDA, y lo que midió

La regla de `apps/web/CLAUDE.md` dice que una región viva vive siempre en el DOM
y lo que cambia es su contenido. El 22 de septiembre de 2026 se midió: de las
114 regiones del frontend cumplían 5, se cerró la clase A —15 sitios, los que
preguntan por la misma señal que el párrafo pinta, `ts-campo` y `ts-select`
incluidos— y **se paró ahí a propósito**. Las 99 que quedan son de tres clases
distintas, treinta plantillas, y se harían para satisfacer una regla **cuyo
efecto real nadie ha observado nunca**: eso es refactorizar contra una creencia.

Este guion era la observación que faltaba. **Se corrió el 22 de septiembre de
2026 y el resultado está al final**: no fue el que se esperaba, y encogió el
trabajo pendiente a un tercio. Queda escrito porque hay que repetirlo cada vez
que se toque una región viva, y porque las dos trampas que costó descubrir se
repiten igual.

Son **treinta minutos con NVDA** y cinco pruebas, cada una sobre una pantalla que
ya existe. **Ninguna gasta dinero ni escribe nada**: no emite guías, no cuenta
inventario, no manda correos.

#### El montaje, que es la mitad del trabajo

1. **NVDA** de `nvaccess.org`, gratis. La versión portable no instala nada y
   sirve igual.
2. **El visor de voz encendido**: menú de NVDA (`NVDA+N`) → Herramientas →
   Visor de voz. Es **el instrumento**, no una comodidad: convierte "me pareció
   oír algo" en un registro de texto que se copia y se pega aquí. Sin él, el
   resultado de esta sesión es un recuerdo.
3. La tecla `NVDA` es `Insert` (o `Bloq Mayús`, si así quedó configurada).
   `Ctrl` calla la voz cuando estorbe; `NVDA+Espacio` alterna modo navegación y
   modo foco.
4. **Chrome, con la pestaña visible y con el foco.** Una pestaña en segundo
   plano no corre `requestAnimationFrame`, así que todo lo que dependa del foco
   da falso negativo — el mismo tropiezo ya documentado para las comprobaciones
   de foco a mano.
5. El sitio arriba: `npm run dev --workspace=apps/web` y `gradlew.bat bootRun`,
   o directamente el ambiente `dev`.
6. **Estrangula la red** en las herramientas de desarrollo (pestaña Red, el
   desplegable de "Sin limitación" a la opción más lenta) **antes de las pruebas
   3 y 5**. En local un estado de carga dura cincuenta milisegundos y no hay
   nada que oír: la prueba saldría negativa por la máquina, no por el código.

**La prueba de control va primero**: abre `/es` y baja con las flechas. Si el
visor de voz no escribe nada, lo que falla es el montaje y ninguna de las cinco
pruebas siguientes significa nada.

#### Las cinco pruebas

Cada una trae **qué se hace**, **qué mirar** y **qué decide**. Anota la línea
del visor de voz, o su ausencia, que es un dato igual de bueno.

**1. La clase A, ya arreglada, sin foco de por medio** — `/es/admin/iniciar-sesion`.
Escribe el correo y una clave equivocada, y pulsa Entrar **una sola vez** (el
limitador cuenta intentos fallidos seguidos; a la quinta bloquea).
El `<p role="alert">` de esa pantalla vive siempre en el DOM y pasa de vacío a
lleno, que es exactamente el patrón que se aplicó en los 15 sitios.
**Qué mirar:** si el visor escribe el mensaje de error **sin que el foco se haya
movido** — se queda en el botón.
**Qué decide:** si no se anuncia, el arreglo de la clase A **no sirvió** y la
deuda 16 se reabre con otro enunciado, porque el problema no sería el `@if`.

**2. La clase C, un estado de pantalla entero** — `/es/cuenta/verificar-correo?token=nosirve`.
El `@switch` pasa por `cargando` y cae en `error`: dos regiones que **nacen ya
llenas**, una `role="status"` y otra `role="alert"`, más un `<h1>` que no está
en ninguna región y sirve de contraste.
**Qué mirar:** el paso de una a la otra. **El "Cargando" de esta pantalla no
cuenta** —se pinta mientras la página todavía está cargando, y ahí ninguna región
viva se anuncia—; lo que mide es si el mensaje de error, que aparece después, se
lee solo. Si además se lee el `<h1>`, es porque NVDA leyó el bloque entero por
otra razón y hay que anotarlo.
**Qué decide:** las 23 de la clase C.

**3. La clase D, el "cargando" que nadie ha oído** — `/es/admin/atencion`, con la
red estrangulada. **No recargues con `F5`**: una región viva que ya está en la
página cuando termina de cargar no se anuncia nunca, por especificación, y la
prueba saldría negativa por el método. Se pulsa uno de los filtros de arriba
—Pendientes, Respondidas—, que relanza la consulta sin recargar la página.
Es un `<p role="status">Cargando…</p>` dentro del `@if` que lo llena, y el
"no hay solicitudes" del `@else if` es otro: el patrón que la regla prohíbe, en
el sitio donde más se repite.
**Qué mirar:** si dice "Cargando" antes de decir el resultado, si dice solo el
resultado, o si no dice nada.
**Qué decide:** las 71 de la clase D, que son las que más diff valen.

**4. La clase B, una región que es de la fila y no de la pantalla** —
`/es/admin/productos/existencias`. Abre "Contar" en cualquier fila y pulsa
guardar **con el formulario vacío**: sale `faltanCampos`, que llena un
`role="alert"` que vive dentro del `@for`. No manda nada al servidor y no
registra ningún movimiento de inventario.
**Qué mirar:** si se anuncia, y si al repetirlo en una segunda fila se vuelve a
anunciar o NVDA lo trata como la misma región.
**Qué decide:** las 5 de la clase B, y de paso si la salida es una región de
página en vez de cinco de fila.

**5. El contraste que separa la región viva del foco** — `/es/admin/productos`,
publicar o retirar un producto (reversible, y en `dev`).
El acuse `#avisoLista` es permanente **y además recibe el foco**.
**Qué mirar:** se va a anunciar, casi seguro. La pregunta es otra: **cuando el
foco se mueve a un nodo, NVDA lo lee por el foco, no por `role="status"`.**
**Qué decide:** nada por sí sola — sirve para no confundir las dos causas al
leer las otras cuatro. Es la prueba que evita concluir de más. **No se corrió el
22 de septiembre**: publicar escribe, y la sonda del final ya contrastó lo mismo
sin tocar datos.

#### Lo que se midió, el 22 de septiembre de 2026

NVDA 2025.3.3 portable, nivel de registro en "Entrada/salida", Chrome al frente,
el sitio en local contra la API local. Cada prueba se aisló marcando el registro
antes de actuar y leyendo solo las líneas nuevas. **El registro no dice lo que
una persona oyó: dice lo que NVDA mandó al sintetizador.** Para esta pregunta es
mejor, porque da el texto literal y no depende de la memoria de nadie.

Las cuatro pruebas dieron un resultado que **no es el que el plan daba por
probable**, y que se ordena solo en cuanto se separa la cortesía de la
permanencia:

| | región permanente, cambia el contenido | nace ya llena dentro del `@if` |
|---|---|---|
| `role="alert"` (asertiva) | **se anuncia** | **se anuncia** |
| `role="status"` (cortés) | **se anuncia** | **calla** |

Las líneas que lo sostienen:

- **Prueba 1**, login del panel con la clave equivocada — `Speaking ['Correo o
  clave incorrectos. ']`, sin que el foco se moviera.
- **Prueba 2**, `verificar-correo` con un token inválido — `Speaking ['alert',
  'El enlace no es válido o ya venció…']`, y esa región **nace ya llena** dentro
  del `@switch`.
- **Prueba 4**, guardar un conteo con el formulario vacío — `Speaking ['alert',
  'Faltan las unidades contadas o el motivo…']`, una región que nace dentro de un
  `@if` **dentro de un `@for`**.
- **Prueba 3**, el acuse de reenvío de verificación — el `role="status"` aparece
  en el árbol de accesibilidad y el registro **no escribe una sola línea**.
- **La sonda**, que es la celda que ninguna pantalla ofrece limpia: se insertó un
  `<p role="status">` vacío, se dejó asentar tres segundos y se le puso texto →
  `Speaking ['sonda permanente cortes llenada despues ']`. La misma región
  insertada **ya con texto** → silencio. Sin foco de por medio en ninguno de los
  dos casos.

#### Lo que esto decide

**La regla de `apps/web/CLAUDE.md` es cierta, y solo para las regiones
corteses.** Para un `role="alert"` da exactamente igual dónde nazca: el lector lo
anuncia igual. Y eso **encoge el trabajo pendiente a un tercio**: de las 111
regiones vivas de hoy, 74 son `role="alert"` y no hay nada que hacerles. De las
37 corteses, 6 ya son permanentes por construcción y **31 nacen dentro de un
`@if`, un `@for` o un `@switch`** — esas son las que hay que arreglar, no 99.
Están concentradas: 8 en el asistente de captura 360, 4 en el panel, 3 en el
panel de retractos, 2 en cada bandeja.

Tocar las de `role="alert"` sería diff sin efecto, que es justo lo que este
guion existía para evitar.

#### Dos trampas del método, que costaron dos pruebas

- **NVDA lee la página entera al cargarla** ("say all" automático, que viene
  encendido de fábrica) y ese modo **se traga los anuncios corteses que lleguen
  mientras lee**. Dos intentos de la prueba 3 salieron en blanco por esto antes
  de entender qué pasaba. Hay que dejar que termine, o medir sobre una acción y
  no sobre una carga.
- **Una región viva que ya está en la página cuando termina de cargar no se
  anuncia nunca**, por especificación. Por eso la prueba 3 se hace pulsando algo,
  nunca con `F5`, y por eso el "Cargando" de `verificar-correo` no cuenta como
  medición.

Y una tercera que no es del método sino de la máquina: en local un estado de
carga dura cincuenta milisegundos. Las dos bandejas que se intentaron tenían
datos y el "Cargando" no llegó a existir el tiempo suficiente. Para medir ese
caso en concreto hay que estrangular la red antes.

#### Un defecto que apareció de paso, y no es de las 111

En la rejilla del catálogo, el mensaje **"No encontramos productos con estos
filtros"** no es una región viva: es un `<p>` sin `role`. NVDA calló con razón.
Quien filtra con un lector de pantalla y se queda sin resultados no se entera de
nada — la rejilla simplemente deja de tener tarjetas. No estaba en las 114
contadas porque no tiene `role` que contar, que es exactamente por qué no se vio.

#### Lo que se hizo con el resultado, el mismo dia

Los 27 sitios se arreglaron en cinco commits, con tres formas segun lo que la caja pinte:

- **Parrafo sin fondo** → la region se queda y el `@if` se mete dentro. Es lo que ya hacia
  `cambiar-clave-admin.page.html` antes de la medicion.
- **Caja con borde o relleno** → un envoltorio permanente alrededor, y la caja sigue condicional.
  Una caja vacia permanente con `p-16` pintaria una barra de color, y eso jsdom no lo atrapa.
- **Contenedor `flex` con `gap`** → el envoltorio lleva `contents`, porque un hijo vacio con caja
  propia abriria un hueco fijo del tamano del `gap`.

**El primer intento del asistente 360 fue una sola region `sr-only` con los textos repetidos, y se
descarto midiendo**: duplicaba el contenido en el DOM —rompio cinco pruebas que buscaban un texto y
encontraban dos— y un lector de pantalla lo habria leido dos veces al recorrer la pantalla. Que una
region viva no duplique contenido visible no es una preferencia de estilo: es lo que evita que todo
se oiga dos veces.

**Cada pantalla gano una prueba** que afirma que la region existe **antes** de tener algo que decir.
Se comprobo rompiendola: devolviendo el parrafo adentro del `@if`, falla. Y tres pruebas del panel
tuvieron que cambiar, porque esperaban la region **por su rol** para saber que los datos habian
llegado: con la region viviendo siempre, `findByRole('status')` resuelve al instante y vacia. Ahora
anclan en el contenido, que es mas fuerte que antes.

**Comprobado con NVDA sobre el codigo arreglado**, la misma noche y con el mismo metodo: la
prueba que habia salido callada —el acuse del reenvio de verificacion— ahora escribe el texto
entero en el registro, y la sonda del envoltorio confirmo que **`display: contents` no saca la
region del arbol de accesibilidad**: un envoltorio vacio con `contents` al que se le mete una caja
dentro se anuncia igual que uno normal. Es lo que sostiene los cinco del asistente 360.

**El numero "regiones dentro de un `@if`" dejo de ser la medida.** Lo que importa es si la region
existe antes de que llegue el mensaje: la de una fila desplegada, o la de un `@case`, nacen dentro
de control de flujo y **si** se anuncian, porque su ambito abre antes de que la persona pulse nada.
Contarlas como pendientes seria perseguir un numero equivocado.

### El guion de TalkBack para el asistente de captura 360

El asistente es la pantalla que más lo necesita y la única que **no se puede
comprobar en el computador**: se usa con el teléfono en la mano, apuntando a un
producto, y la mitad de su contenido depende de la cámara y del acelerómetro. El
guion de NVDA de arriba sirve igual aquí, con TalkBack en vez de NVDA, y con un
montaje que cuesta más.

Esto es la segunda mitad de la deuda 32 del plan. La primera —cada cuánto habla
el nivel— se decidió midiendo, y está en `nivel-360.ts`.

#### El montaje

```
docker compose up -d                                   # desde la raíz
gradlew.bat bootRun                                    # en apps/api
npm run dev --workspace=apps/web -- --allowed-hosts    # desde la raíz
cloudflared tunnel --url http://localhost:4200
```

La bandera `--allowed-hosts` no es opcional: sin ella el dev server rechaza el
host efímero del túnel. El detalle está en `apps/web/README.md`, junto con el
motivo por el que hace falta HTTPS —la cámara, el sensor y `crypto.subtle` solo
existen en contexto seguro— y la advertencia de que mientras el túnel esté arriba
la aplicación de desarrollo, panel incluido, es accesible desde internet.

Hace falta además lo que ninguna herramienta sustituye: **la cámara, un producto
y luz**. Y TalkBack encendido antes de abrir la pantalla, no después.

Si el recorrido va a llegar hasta publicar el set, el bucket necesita conocer el
host del túnel o el `PUT` firmado muere en el preflight de CORS — el comando está
en `apps/web/README.md`. Para la parte de accesibilidad no hace falta: los pasos
1 a 6 del asistente no suben nada.

#### Las cinco comprobaciones

Con TalkBack, el gesto de explorar es **deslizar a derecha e izquierda** para ir
al siguiente elemento y **doble toque** para activar. Anota lo que oigas, o su
ausencia, que es un dato igual de bueno.

**1. La pantalla se puede recorrer sin verla.** Desliza desde arriba hasta abajo
antes de tocar nada.
**Qué mirar:** que las condiciones mínimas —fondo, luz, centrado, distancia— se
oigan, que los botones de cantidad de fotogramas digan cuántos son, y que el
botón de la cámara diga qué va a hacer.
**Qué decide:** si algo no se oye, no está en el árbol de accesibilidad.

**2. El permiso de la cámara.** Doble toque en "Permitir la cámara".
**Qué mirar:** que al volver del diálogo del sistema se anuncie el cambio de
estado, y que aparezca el botón de disparar.

**3. El nivel, que es el corazón de la deuda.** Con la cámara abierta y el nivel
activado, sostén el teléfono apuntando al producto y muévelo despacio dentro y
fuera de la tolerancia.
**Qué mirar:** cuántas veces habla. Tiene que decir frases como «Inclina el
teléfono 7 grados hacia adelante» y **quedarse callado mientras el estado no
cambie**, no recitar cada grado. La cuenta esperada es de unos pocos anuncios por
minuto.
**Qué decide:** si habla sin parar, el asentamiento del anuncio no está llegando
a la región — el `aria-hidden` del texto visible y el `role="status"` de la
región viva están en `ts-indicador-nivel.html`.

**4. El obturador bloqueado.** Tuércelo hasta que el botón se deshabilite.
**Qué mirar:** que el aviso **no** se anuncie por su cuenta —eso es
deliberado, lo explica el nivel— y que el botón de disparar se anuncie como
deshabilitado cuando se le llega deslizando.
**Qué decide:** si el aviso habla además del nivel, se está diciendo lo mismo dos
veces.

**5. El set completo.** Captura las tomas prometidas, aceptando cada una.
**Qué mirar:** que el progreso se oiga al cambiar de toma, que el acuse de "listas
las N tomas" se anuncie, y que al final el visor de revisión sea alcanzable.

#### Lo que se corrió, el 23 de septiembre de 2026

Se hizo el recorrido entero en un Android con TalkBack, con la cámara, el
producto y el set completo, sobre el código ya arreglado. **Las cinco
comprobaciones pasaron y no apareció ninguna anomalía.** Con eso queda cerrada la
mitad de la deuda 32 que pedía el aparato delante.

**Cómo se anotó, que importa para saber cuánto vale.** Esta medición **no tiene
registro**: es lo que oyó una persona, no un archivo de texto que se pueda pegar
aquí. La de NVDA de más arriba sí lo tiene, porque el visor de voz escribe lo que
manda al sintetizador y TalkBack no ofrece un equivalente cómodo. Así que esta
vale para lo que vale — un «suena bien» de quien usa la pantalla — y lo que
sostiene la conducta entre corrida y corrida son las pruebas de
`nivel-360.spec.ts` y `ts-indicador-nivel.spec.ts`, que fallan si el anuncio
vuelve a hablar en cada grado o si el texto visible deja de estar oculto al
lector.

Si algún día hace falta un registro literal, la vía es TalkBack con la salida de
desarrollo activada y `adb logcat`, que no se probó.

#### Lo que ya se sabe sin encender TalkBack

Los cinco envoltorios de `display: contents` del asistente se apoyan en una sonda
que respondió que sí —esa propiedad no saca la región del árbol de
accesibilidad—, y eso es el mecanismo, no la pantalla. Este guion es lo que
convierte el mecanismo en una observación.

### Lo que Vitest no atrapa en la capa visual

Encontrado en la Fase 2 del stack de UI (2026-09-07, `ADR-0020`). Las tres cosas
pasaron de verdad y ninguna prueba las vio.

- **Una clase de Tailwind que no existe no falla: no hace nada.** Las escalas por
  omisión están borradas, así que `min-h-0` y `min-h-auto` no existen — y una
  prueba que compruebe `className` las encuentra igual, porque la clase *está* en
  el atributo. **La única comprobación válida es leer el CSS compilado** y buscar
  el selector, escapando la barra invertida (`.focus-visible\:outline-2`). Un
  escapado de menos da falsos negativos: pasó dos veces.
- **`cn()` puede borrar una clase correcta.** `tailwind-merge` no conoce nuestro
  vocabulario: creyó que `font-medio` era una familia tipográfica, la fusionó con
  `font-texto` y **el botón se pintaba en Arial**. La clase estaba en el
  componente y desaparecía al fusionar, así que ninguna prueba de componente
  podía verlo — se encontró recorriendo el sitio en el navegador. Los grupos
  están declarados en `cn.ts` con pruebas de regresión, y ahí hay que registrar
  cada token nuevo con nombre no numérico.
- **El foco no se prueba en jsdom.** Las utilidades `focus-visible:` solo aplican
  cuando el navegador considera el foco "visible", y eso depende de la modalidad:
  con Tab sí, con clic en un `<input>` no siempre. Y la trampa de foco del CDK
  (`[cdkTrapFocus]`) usa `InteractivityChecker`, que mide layout — en jsdom todo
  mide cero. **Ambas se verifican a mano en `ng serve`.**
- **Cuál variante de imagen descarga el navegador tampoco se prueba en jsdom.**
  No evalúa `srcset` ni `sizes`: la prueba puede comprobar que el atributo está
  bien escrito —y lo hace, en `ts-galeria.spec.ts`— pero no que el navegador
  eligiera el ancho correcto. Eso se mira en las herramientas de desarrollo, en
  la pestaña de red, o en los bytes que reporta `image-delivery-insight` del
  arnés de Lighthouse, que son la parte de ese informe en la que sí se puede
  confiar (`ADR-0057`).
  **Y una prueba de `srcset` sin `IMAGE_LOADER` registrado no prueba nada**:
  NgOptimizedImage no emite el atributo con el loader por omisión, así que la
  aserción pasaría sobre un `srcset` vacío. Las pruebas que lo miran registran el
  mismo loader que `app.config.ts`.

### Esperas en las pruebas de componente

`await esperar(ms)` con un número fijo es frágil: pasa en aislamiento y falla en
la suite completa, cuando la máquina está cargada. Le ocurrió a
`agregar-variante-admin.page.spec.ts`, que se cayó al cerrar la fase por 50 ms
insuficientes.

**Para esperar que algo aparezca, `findByText`/`findByRole` de Testing Library**,
que sondean hasta que el elemento existe. `esperar(ms)` se reserva para lo que de
verdad es una espera de tiempo —el *debounce* de los filtros, o el registro
asíncrono de `PendingTasks` que describe `apps/web/CLAUDE.md`—, no para "que
termine de pintar".

**Para esperar a un espía o a un contador, `vi.waitFor`**, con **todas** las
aserciones del grupo dentro. Esperar solo por la primera no basta: la llamada al
repositorio ocurre antes de que el DOM se actualice, así que la segunda
aserción corría demasiado pronto — pasó en tres pruebas al hacer justamente eso.

Se barrieron todas. **Quedan tres, y las tres son correctas**: el loader de
Transloco retardado a propósito y las dos del *debounce* de los filtros, donde lo
que se prueba **es** el paso del tiempo. Ahí `vi.waitFor` pasaría al instante y
no probaría nada.

**Un número fijo de *vueltas* es lo mismo que un número fijo de milisegundos**, y
cuesta más verlo. `captura-360.page.spec.ts` esperaba con `asentarVarias`, que
asienta catorce veces y ya; el 22 de septiembre de 2026 la verificación web falló
con `expected Array(3) to have a length of 4` — tres de los cuatro fotogramas
subidos. Dos cosas que conviene tener presentes:

- **No era cuestión de microtareas.** El hash de cada fotograma es un
  `crypto.subtle.digest` de verdad, así que cuántas vueltas hacen falta depende de
  lo cargada que esté la máquina. Un bucle de `await` no "termina el trabajo
  pendiente": solo cede el turno unas cuantas veces.
- **Lo destapó un PR que no tocaba captura360**: solo añadía un archivo de pruebas.
  Vitest reparte los archivos entre trabajadores, así que **sumar uno cambia la
  carga de los demás** y basta para que una prueba frágil se quede sin vueltas. Si
  una prueba empieza a fallar en un PR que no la toca, sospecha de esto antes que
  del cambio.

La salida es la de siempre: `vi.waitFor` con las aserciones dentro. Si además hace
falta empujar la detección de cambios, el callback puede ser `async` y llamar a
`asentar(fixture)` en cada intento.

**Ojo con cuál `waitFor` se usa**: el de `@testing-library/angular` tipa su callback
como síncrono (`() => never`) y no acepta uno `async` — el compilador lo dice, pero
el mensaje no es evidente. Para esperar por un contador o un espía, `vi.waitFor`.

Tres lecciones que costaron un fallo cada una:

- **`waitFor` tiene que cubrir lo último que ocurre, no lo primero.** Esperar a
  que el repositorio reciba la llamada y luego comprobar el DOM deja la segunda
  aserción corriendo antes de tiempo: la llamada resuelve antes de que la
  pantalla se repinte. Lo mismo con una navegación que sucede después de crear.
- **Si el resultado se ve en la pantalla, `findBy*`**; si no se ve —un espía, un
  contador, una señal del store—, `vi.waitFor`.
- **Cuando no hay ninguna señal en el DOM**, se espera por la fuente. En
  `confirmar.page.spec.ts` el subtotal es 0 aunque el carrito ya haya cargado,
  porque ese escenario no tiene snapshot: la señal real es
  `carritoStore.consulta.data()`, y se obtiene del inyector del fixture.

Al quitar las esperas, varios helpers `esperar(ms)` quedaron sin uso. Se
borraron: un ayudante muerto es una invitación a volver al patrón viejo.

Nada de instantáneas de HTML: se rompen con cualquier cambio de estilo y no dicen
nada. Nada de pruebas que solo verifican que el componente se construye. Nada de
consultas por clase CSS **para comprobar comportamiento**; comprobar que una
variante aplica su color de fondo sí es legítimo, porque las clases *son* el
estilo y no hay nada más que lo atrape.

## En integración continua

En cada pull request: compilar, lint, pruebas de las dos aplicaciones, ArchUnit,
verificación de que `es.json` y `en.json` tienen las mismas claves, y
`terraform plan`. Si algo de eso falla, no se mezcla.

Cobertura: se mide, no se persigue. Un noventa por ciento con pruebas triviales es
peor que un sesenta donde lo cubierto es dinero, inventario y pagos.

## Recorridos completos con Playwright

Existen desde la Fase 6 (`npm run e2e`, `apps/web/e2e/`). Dos: la compra completa
—portada, ficha, carrito, checkout con la casilla de autorización, transferencia,
pedido creado— y la navegación legal desde el pie.

**Fuera de `npm run verificar` a propósito.** Necesitan `docker compose up -d`,
`gradlew.bat bootRun` y `npm run dev` levantados; `verificar` tiene que seguir
corriendo en seco.

**Usan el Chrome instalado (`channel: 'chrome'`)**, no el Chromium que Playwright
descarga: esa descarga falla en esta máquina, y probar contra el navegador que de
verdad usan los compradores es más fiel.

### Qué atrapan que Vitest no

En la primera corrida encontraron un defecto real que las pruebas unitarias no
veían: en el resumen del checkout, quien no marcaba la casilla de autorización
pulsaba «Continuar» y **no pasaba nada**. La prueba de Vitest comprobaba que no se
guardara el borrador —cierto— pero no que se le dijera al comprador por qué. Esa
es exactamente la diferencia entre probar el estado y probar el recorrido.

## Lighthouse, con arnés

`npm run lighthouse` mide las tres pantallas —portada, ficha y legales— sobre el
build de producción, en móvil y con estrangulamiento. Necesita
`docker compose up -d` y `gradlew.bat bootRun`, y por eso está fuera de
`npm run verificar`, igual que los recorridos. `--sin-build` reutiliza el `dist`
que ya exista.

**No es un script, es un arnés**, y la diferencia son las dos cosas que se niega
a hacer en silencio:

- **Levanta un proxy** que manda `/api` al backend y el resto al servidor SSR,
  que es lo que hace el balanceador en producción y lo que `ng serve` hace en
  desarrollo. Servir el build a secas mide otra aplicación: la consulta del
  producto muere tras hidratar.
- **Comprueba que la ficha cargó** antes de medir. Si sale con `noindex` es que
  el producto no llegó, y las cifras que salgan después no significan nada. Fue
  exactamente lo que invalidó la primera medición de la Fase 6, sin que nadie lo
  notara mirando los números.

**Mide tres veces cada pantalla y reporta la mediana**, con la dispersión al
lado: una sola muestra puede inventar una regresión o tapar una real. Las
muestras crudas quedan en `apps/web/lighthouse/resumen.json` y el informe
completo de la corrida mediana en `<pantalla>.json`.

**La regla de uso importa tanto como la herramienta**: el estado de la máquina
mueve el rendimiento más que casi cualquier cambio de código —la misma portada,
con el mismo build, dio 57 y 84 en dos sesiones de la misma noche—. Se mide
antes y después del cambio **en la misma sesión**. Una tabla de otro día no es
una línea base. La historia completa está en `docs/09-plan-de-arranque.md`.

Para eso están las etiquetas: `--etiqueta base` guarda la corrida en su propia
carpeta con siete métricas por muestra, y `--comparar base cambio` enfrenta dos
guardadas sin volver a medir —no necesita ni API, ni build, ni Chrome—.

**Lo que la comparación nunca dice es "sí" para un tiempo**, y conviene saber
por qué antes de pelearse con ella. Se midió el mismo build dos veces, cinco
minutos aparte: la evaluación de scripts de la portada se movió 198 ms y el
rendimiento 4 puntos **sin que nadie cambiara nada**. Las tres muestras de una
corrida son consecutivas, así que su dispersión mide lo que varía en treinta
segundos, no entre dos corridas separadas por un build. De ahí el piso por
métrica —cifras de ese control, no un porcentaje a ojo— y de ahí que lo más que
se pueda decir de una mejora de tiempo sea *"quizá: repite el par"*. Los bytes
sí se afirman: no dependen del reloj.

**Repetir el par significa cuatro corridas en orden invertido**, no dos más:
`sin`, `con`, `con`, `sin`. Si la máquina se va calentando durante los veinte
minutos del experimento, medir siempre en el mismo orden le regala la mejora al
segundo; invirtiendo la segunda pareja, un arrastre monótono empuja a las dos en
sentidos contrarios y se ve. Así se midió la hidratación diferida, y así se
descubrió que los 145 ms que se le habían atribuido eran **40** —el efecto era
real, la cifra no—. Dos parejas que coinciden en signo no demuestran nada por sí
solas: hacen creíble lo que cada una por separado no puede afirmar.

**Y hay un límite que conviene saber antes de creerle una cifra absoluta.** El 22 de septiembre se
fue a por los "700 ms de estilo y layout" de la portada y no existían: ese desglose viene
multiplicado por el factor de estrangulamiento —en la traza son 190 ms— y, sobre todo, el arnés
informaba un primer píxel a 1,3-1,6 s en las pantallas con imágenes cuando **el mismo build, en un
navegador de verdad, pinta en 344 ms**. No se encontró la causa dentro del arnés. Así que:

- **De sus cifras absolutas de FCP y LCP no se puede deducir lo que ve una persona** en pantallas
  con imágenes. `legales`, sin ninguna, no tiene ese hueco.
- **Sus bytes, sus desgloses de trabajo y sus comparaciones entre dos corridas suyas sí valen**:
  todo eso se mide contra sí mismo, que es para lo que se construyó.
- Cuando una cifra absoluta parezca mala, **compruébala en el navegador antes de optimizar nada**.
  `--traza` guarda la traza de Chrome junto al informe y `--con-ventana` mide con un Chrome
  visible; las dos se añadieron ese día, y sin ellas el número falso habría pasado por bueno.

**Ese hueco tiene consecuencia medible, y desde el 23 de septiembre el arnés la etiqueta.** La
causa del fotograma retenido sigue sin encontrarse —no se reproduce en un navegador normal, ni
frío ni caliente, y va y viene entre muestras de la misma corrida—, pero lo que le hace al puntaje
sí está medido: cuando el fotograma se retiene, las tipografías **alcanzan a terminar de bajar
antes del primer pintado**, y el simulador le cobra al FCP todo byte que terminó antes que él. Son
273 KiB a 184 KB/s: **1,45 s de FCP y unos 22 puntos**. Seis muestras seguidas, seis aciertos.

Por eso cada muestra lleva ahora dos cifras más en `resumen.json`: `fcp observado` —el reloj, no el
modelo— y `tipografias antes del fcp`. Leerlas cambia el diagnóstico de una corrida:

- Muestras con el mismo número: la dispersión que quede es ruido de verdad.
- Muestras con números distintos: **no se midió tres veces, se midieron dos cosas**, y la mediana
  no lo arregla porque el artefacto **solo suma**. El arnés lo dice en voz alta al terminar, y
  `--comparar` marca esa fila con "ojo: midieron en modos distintos".
- Las muestras con más tipografías dentro miden el fotograma retenido, no la página.

Esto **no arregla** la retención: la hace visible. Una corrida cuya portada cayó entera del lado
retenido sigue sin poder compararse con otra que no.

Eso lo hace `npm run pareja` y no hace falta montarlo a mano:

```
npm run pareja -- --antes <commit> --despues <commit> --prefijo defer
npm run pareja -- --solo-resumen defer     # el veredicto otra vez, sin medir
```

Saca del diff los archivos que cambian entre los dos commits **dentro de lo que
entra al build** —y los imprime, porque un experimento cuyo contenido no se ve no
vale—, alterna el orden, y al final dice de cada métrica si el signo **se
repitió**. Se niega a empezar si hay cambios sin confirmar en esos archivos (los
sobrescribe) o si la API no responde, que después de veinte minutos de corridas
duele más. Y devuelve el árbol a su sitio aunque se corte a la mitad con Ctrl+C,
que es justo lo que uno hace cuando ve venir un resultado malo.

**Lo que entra al build son tres carpetas, y durante un tiempo fue una.** Hasta
el 23 de septiembre de 2026 solo miraba `apps/web/src`, y así dos clases de
cambio quedaban fuera **sin que nada lo dijera**:

- **`packages/marca`**, el kit. `prebuild` corre `copiar-marca.mjs` y lo copia
  dentro de `apps/web/src/assets/marca` y de `apps/web/public` en cada build, así
  que intercambiar la copia no servía de nada: `prebuild` la volvía a pisar y las
  dos mitades del experimento salían del mismo build.
- **`apps/web/public`**, que no está bajo `src` y es donde vive el hero de la
  portada. El experimento del hero del 22 de septiembre no se habría podido
  montar con esta herramienta.

Y queda un guardián para el caso que el alcance no arregla solo: si el diff toca
`apps/web/src/assets/marca/` **sin** tocar `packages/marca`, se niega y dice
dónde está el original. Eso solo pasa si alguien editó a mano un archivo
generado, que es lo que prohíbe la regla 3 del `CLAUDE.md`.
