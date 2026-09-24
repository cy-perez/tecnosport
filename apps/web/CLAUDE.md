# apps/web — reglas

Angular 22.5 · SSR con hidratación · standalone · zoneless con signals ·
Tailwind CSS v4 sobre los tokens de marca · Transloco (es/en) · TanStack Query ·
Angular CDK · Vitest · npm · PWA.

Lee `docs/04-ui-marca.md` antes de escribir un solo estilo. El sistema visual ya
existe y está cerrado.

## Estructura

Por funcionalidad, y dentro de cada una, las capas del backend:

```
src/app/features/catalogo/
  domain/          modelos y puertos. Sin HttpClient.
  application/     casos de uso y stores de signals
  infrastructure/  adaptadores HTTP, mapeadores, queries de TanStack
  presentation/    componentes y rutas
```

Funcionalidades: `catalogo`, `carrito`, `checkout`, `cuenta`, `admin`,
`captura360`.

Un componente **nunca** inyecta `HttpClient` ni una clase de `infrastructure`:
inyecta el puerto declarado en `domain`, y el proveedor de la ruta decide la
implementación. Lo verifica **`npm run capas`**, no ESLint — ver la regla dura
#1 de `CLAUDE.md` para saber por qué.

**`core/` entra en ese grafo desde el 24 de septiembre de 2026, con la misma
regla que `shared/`**: lo usa la aplicación entera, así que no puede depender de
una funcionalidad. Si necesita un tipo, lo declara él —el cargador de imágenes lo
hace con `VarianteDeImagen`—. Estuvo fuera del grafo desde el principio, y ahí
viven la sesión, el HTTP, el i18n, el SEO y el `IMAGE_LOADER` global, que
importaba el modelo de `catalogo`.

**`layout/` sigue fuera, y es una decisión.** Componer funcionalidades es su
trabajo: la insignia del encabezado tiene que ver el mismo carrito que la ficha.
Meterlo en la regla convertiría un diseño decidido en un aviso permanente.

## Reglas concretas

- **Todo componente es standalone, `OnPush`, y usa `signal`, `computed`,
  `input()` y `output()`.** Nada de decorador `@Input()`, nada de `Subject` para
  estado local, nada de `zone.js`.
- **Datos remotos: TanStack Query, siempre.** `staleTime` explícito por consulta.
  Las mutaciones invalidan por clave; no se recarga la página.
- **Estado del carrito:** store de signals persistido en `localStorage` y
  reconciliado contra el servidor al entrar al checkout. El precio, la existencia
  y el costo de envío **los recalcula el backend** antes de cobrar. Lo que hay en
  el navegador es una intención, no una verdad.
- **`CarritoStore` es `@Injectable({providedIn: 'root'})`, no una función de
  fábrica como `usarBusquedaProductos`.** El id del carrito lo necesitan tres
  componentes independientes (`encabezado`, la ficha de producto, la página del
  carrito) y tienen que ver el mismo carrito — una fábrica le daría a cada uno
  su propia señal aislada, y el badge del encabezado nunca se enteraría de que
  la ficha acaba de crear un carrito. Un servicio singleton es la excepción
  correcta: úsala solo cuando de verdad hay estado compartido entre
  componentes que no tienen relación padre-hijo, no como patrón por defecto.
- **El carrito es la única excepción documentada a "siempre precargar en el
  resolver" de `ADR-0011`.** El id del carrito vive en `localStorage`, anónimo,
  sin cookie de sesión — el servidor no tiene forma de saber qué carrito es
  "el de este visitante", así que no hay nada que precargar del lado del
  servidor. `CarritoStore.carritoId` arranca en `null` y se llena en
  `afterNextRender` (mismo patrón que la cookie de tema en `encabezado.ts`):
  el SSR de `/carrito` sirve siempre "carrito vacío", nunca un *esqueleto*
  colgado, y el cliente carga el carrito real justo después de hidratar.
- **Estilos: Tailwind, no SCSS** (`ADR-0020`). Las escalas por omisión están
  borradas: solo existen utilidades mapeadas a `tokens.css` — `bg-ts-primario`,
  `text-ts-texto-suave`, `p-16` (que son **16 px**, no 64), `max-w-formulario`.
  `rounded-sm/md/lg` y `rounded-completo` **sí** existen desde el 24 de
  septiembre de 2026 y salen de `--radio-*` (6, 8, 12 px y la píldora); lo que
  no existe es `rounded-xl` ni ningún literal tipo `rounded-[10px]`. El chaflán
  sigue siendo la clase `.chaflan` de `tokens.css`, y ya **no** es el patrón de
  la interfaz: quedó reservado a la marca (`ADR-0060`). Las superficies que
  todavía lo llevan —`ts-boton`, `ts-tarjeta-producto`, `ts-dialogo`, el hero y
  el enlace de salto— están pendientes de migrar.
  Un `.scss` nuevo por componente es un olor: si hace falta, dilo antes de
  escribirlo.
  - **Una clase que no existe no falla, no hace nada.** No hay linter que avise.
    Pasó con `min-h-0` y `min-h-auto`, que no existen porque la escala de
    espacio está borrada. Al usar una utilidad de la que no estés seguro,
    **compila y lee el CSS**.
  - **Las clases se componen con `cn()`** (`shared/ui/cn.ts`), que resuelve
    conflictos para que la clase de quien llama gane sobre la base del
    componente. `cn` conoce el vocabulario de tokens del proyecto porque se lo
    enseñamos ahí: **al añadir un token con nombre no numérico hay que
    registrarlo**, o dos utilidades del mismo grupo dejan de competir en
    silencio.
- **`shared/ui/` es la capa tonta.** `input()` y `output()`, y **nada de
  Transloco, TanStack Query ni dominio**: los textos llegan traducidos por quien
  la usa. `shared/` a secas guarda lo que todavía traduce o conoce un modelo.
  Un componente nuevo de sistema de diseño va en `shared/ui/`.
- **Modo oscuro:** atributo `data-tema` en `<html>`, dos opciones (claro y oscuro)
  alternadas con un botón, persistido en cookie y resuelto en el servidor para no
  parpadear al hidratar. Sin cookie manda el `prefers-color-scheme`, resuelto en
  el script inline de `index.html` — valor inicial, no preferencia guardada.
  **El control no guarda estado**: `data-tema` es el estado aplicado y el icono
  lo decide el CSS, porque el servidor no conoce el tema mientras renderiza.
- **Accesibilidad no es una fase final.** Todo control alcanzable por teclado, el
  anillo de foco no se elimina jamás, diálogos con el CDK y trampa de foco,
  imágenes con `alt` traducido, `label` real en cada campo.
- **Un `[attr.aria-label]` puesto directamente en la etiqueta de un componente
  compartido (`<ts-boton aria-label="...">`) cae en el host del componente, no
  en el elemento real dentro de su plantilla.** Angular solo redirige un
  binding de atributo al elemento interno si el componente lo declara como
  `input()`; si no, el atributo se queda en `<ts-boton>` y el nombre accesible
  del `<button>` real no cambia — encontrado al escribir las pruebas de
  `linea-carrito` (`getByRole('button', { name: ... })` no encontraba el
  botón). Por eso `ts-boton` tiene un input `etiquetaAccesible` que sí se
  liga con `[attr.aria-label]` sobre el `<button>` interno. Cualquier
  componente compartido que envuelva un control nativo y necesite exponer
  ARIA más allá del contenido proyectado necesita el mismo input explícito.
- **Una acción de fila nunca usa `[cargando]`, usa `[ocupado]` más una guarda de
  reentrada en el manejador.** `cargando` hace dos cosas —marca `aria-busy` y
  **deshabilita**— y la segunda tiene un precio: deshabilitar el botón que la
  persona acaba de pulsar le quita el foco, y el navegador lo manda a `<body>`.
  En una fila —publicar, contar, medir, quitar— eso devuelve al principio del
  documento a quien navega con teclado. El defecto costó dos correcciones en una
  semana y seguía vivo en cuatro pantallas del panel. `cargando` se queda para
  el envío de un formulario de página completa, donde no hay foco de fila que
  perder.
- **Toda interacción que destruye su propio disparador devuelve el foco a mano.**
  Cancelar una confirmación en línea, o confirmarla, borra la caja con el botón
  dentro. `usarFoco()` (`shared/foco/`) es el `requestAnimationFrame` con
  guardia de plataforma; el destino es el botón que abrió la caja al cancelar, y
  el aviso de resultado al confirmar. **En jsdom no se reproduce**, así que la
  prueba no lo va a atrapar: se comprueba en el navegador, con la ventana
  delante — en una pestaña oculta no corre `requestAnimationFrame` y el
  documento no retiene `activeElement`, así que da falso negativo siempre.
- **Un botón que abre una región la declara**: `expandido` y `controla` de
  `ts-boton`, que van al `aria-expanded` y al `aria-controls` del `<button>`
  real. Sin ellos, pulsar "Contar las unidades de SKU-X" no anuncia nada y
  descubrir que apareció un formulario es tabular a ciegas.
- **Una región viva cortés vive siempre en el DOM y lo que cambia es su
  contenido.** Montar un `role="status"` ya lleno con un `@if` es justo lo que
  los lectores de pantalla anuncian mal — **medido con NVDA el 22 de septiembre
  de 2026: calla**. Con `role="alert"` da igual dónde nazca, porque es asertiva y
  se anuncia siempre; no hay que envolver ni mover las que ya existen. Y al
  revés: `role="status"` no va en el resumen estático de una tabla — cada
  revalidación en segundo plano lo vuelve a leer en voz alta. El guion de la
  medición y sus trampas están en `docs/06-testing.md`.
- **No se deshabilita un botón para decir que faltan datos.** Un
  `<button disabled>` sale del orden de tabulación: quien navega con teclado no
  lo encuentra y nada le explica por qué no pasa nada. Se deja vivo, se valida al
  pulsar y se dice qué falta, con `markAllAsTouched()` más un mensaje de
  Transloco.
  - **`markAllAsTouched()` sin un `[error]` enganchado no pinta nada.** Las cinco
    pantallas de autenticación ya lo llamaban y ninguna tenía un solo `[error]`,
    así que el marcado no se veía por ninguna parte y el botón deshabilitado era
    la única pista. Las dos mitades van juntas o no va ninguna.
- **Un campo obligatorio lo declara: `[obligatorio]="true"` en `ts-campo` o
  `ts-select`.** Pinta el asterisco y, lo que importa, pone `aria-required="true"`.
  Había 65 `Validators.required` en el frontend y **cero** `required` o
  `aria-required` en las plantillas: con lector de pantalla, "Correo, editar" no
  dice que haga falta.
  - Es `aria-required` y **no** el `required` nativo: el nativo dispara la burbuja
    del navegador, sin traducir y fuera del sistema visual, y aquí se valida al
    enviar con mensajes de Transloco.
  - **El asterisco va fuera del `<label>`.** Dentro, aunque lleve `aria-hidden`
    —que lo saca del nombre accesible de verdad—, se cuela en el `textContent` de
    la etiqueta y `getByLabelText('Correo electrónico')` deja de encontrar el
    campo. Lo destaparon nueve pruebas al primer intento.
- **Imágenes:** `NgOptimizedImage` siempre. Una imagen de producto se publica en
  **varias resoluciones** (`ADR-0057`) y la plantilla las ofrece todas: `ngSrc`
  con la mayor, `ngSrcset` con `descriptoresDe(imagen)` —los anchos, no las
  URL—, `loaderParams` con `{ variantes }` y un `sizes` de
  `core/imagenes/tamanos-de-imagen.ts`. Quien resuelve la URL de cada ancho es
  el `IMAGE_LOADER` registrado en `app.config.ts`, que **busca la variante en
  `loaderParams`** y no la deduce de la forma de la key: ese acoplamiento
  silencioso es el que produjo `url_webp`.
  - **Sin loader no hay `srcset`.** `NgOptimizedImage` salta la generación
    cuando el loader es el de por omisión, y un `ngSrcset` sin loader repetiría
    la misma URL en todos los descriptores (aviso 2963). Comprobado en la
    fuente de `@angular/common` instalada.
  - **Una imagen que se publica en un solo ancho lleva `disableOptimizedSrcset`**
    —el hero de la portada, la línea del carrito, los fotogramas del visor 360—,
    o Angular anuncia un 2x que es exactamente el mismo archivo.
  - Los descriptores viven en **una sola función del dominio**, igual que antes
    vivía ahí la elección de formato: así fue como el visor terminó sirviendo la
    WebP y la galería el original sin que nadie lo hubiera decidido.
  - **Con `width` y `height`** cuando la imagen se pinta con la relación de
    aspecto del archivo. **En modo `fill`, dentro de un marco con
    `position: relative` y `aspect-ratio`, cuando el recorte lo decide el CSS**
    (`object-fit: cover`) — es el caso de `ts-tarjeta-producto` y de
    `ts-galeria`. Declarar el ancho y el alto reales del archivo mientras la
    hoja de estilos recorta a otra relación son dos verdades que no coinciden, y
    Angular avisa (`NG02952`) con razón: no se silencia el aviso, se elige el
    modo correcto.
  - **`priority` solo en la candidata a LCP de cada pantalla**, nunca en más de
    una: priorizar todo es no priorizar nada. Es el fotograma frontal de la
    ficha, y la **primera tarjeta** de la portada y de la rejilla — la portada no
    tiene `<img>` de hero, su hero es un bloque de CSS, así que el LCP le toca a
    esa tarjeta (`NG02955`). Por eso `ts-tarjeta-producto` recibe
    `prioritaria` como `input()` en vez de decidirlo por su cuenta: quién es la
    primera lo sabe la pantalla, no el componente. `ts-galeria` tiene el mismo
    input y **por omisión vale `false`**: el valor por defecto tiene que ser el
    que no hace daño, así que una pantalla nueva que se olvide de decidir se
    lleva una imagen sin priorizar y no una segunda candidata a LCP compitiendo
    con la de verdad. La pantalla lo declara explícito aunque coincida con el
    defecto.
  - **Las entradas de `NgOptimizedImage` distintas de `ngSrc` están congeladas
    tras inicializar** (`assertNoPostInitInputChange`, comprobado en la fuente de
    `@angular/common`). `ngSrc` sí se puede cambiar en caliente — por eso el
    visor 360 es un solo `<img>` que cambia de `ngSrc` y no N apilados. Y por eso
    una plantilla que muestre la misma imagen con `prioritaria` distinta según la
    rama repite el elemento en cada rama con un literal, en vez de ligar el input
    a una expresión: ligarlo revienta al navegar entre dos pantallas que no
    coinciden.
- **SSR:** nada de `window`, `document`, `localStorage`, `navigator` ni sensores
  fuera de un guardia de plataforma. Las consultas de la primera pantalla se
  precargan en el `resolve` de la ruta, no dentro del componente — ver "Notas
  de SSR con TanStack Query" más abajo y ADR-0011.
- **Rutas:** `loadComponent` o `loadChildren` en todo. Nada eager salvo el
  layout. `/admin` y `captura360` en sus propios bundles.
- **Estado de listas y filtros: en los query params de la URL, nunca en una
  señal de componente.** Compartible, sobrevive un refresh, funciona con
  "atrás" del navegador. Ver ADR-0011.
- **Formularios reactivos y tipados.** Los mensajes de error salen de
  Transloco. Un control de formulario propio en `shared/` (`ts-campo`,
  `ts-select`) implementa `ControlValueAccessor` para que un `FormGroup` real
  pueda bindearlo con `formControlName` — no un `[(ngModel)]` ni un
  `@Input()`/`@Output()` de valor suelto.
- **El modelo del front es del front.** El DTO generado se mapea a un modelo
  propio en `infrastructure`; los componentes no ven la forma de la respuesta HTTP.

## APIs del navegador

`getUserMedia`, `DeviceOrientationEvent`, `Canvas`, `PointerEvent` y `Wake Lock`
se usan solo en `captura360` y siempre detrás de:

1. Guardia de plataforma, porque no existen en el servidor.
2. Verificación de disponibilidad real.
3. Solicitud de permiso disparada por un gesto del usuario.
4. **Un camino degradado cuando el permiso se niega o la API no existe.**

Detalle en `docs/10-captura-360.md`.

## Notas de SSR con TanStack Query

Encontrado de forma empírica en Fase 1 (rejilla, filtros, ficha) — que quede
escrito para no repetirlo. Detalle completo en ADR-0011.

- **`injectQuery`/`injectInfiniteQuery` no son deterministas en SSR por sí
  solos.** Se integran con `PendingTasks` de Angular (lo que le dice al SSR
  "espera antes de serializar"), pero ese registro ocurre dentro de un
  `effect()`, agendado async — no a tiempo del primer render. Sin precarga,
  la misma página a veces sirve datos y a veces *esqueletos*, según qué tan
  rápido responda el backend. Nunca fallar en seco: solo notarlo comparando
  el HTML servido con `curl` en dos corridas.
- **La corrección es precargar en el `resolve` de la ruta**, con
  `queryClient.prefetchQuery`/`prefetchInfiniteQuery`, usando la *misma*
  función de opciones (`queryKey`/`queryFn`/`staleTime`) que usa el
  componente — nunca repetirla a mano en los dos lados. Ver
  `application/buscar-productos.consulta.ts` como plantilla
  (`opcionesBusqueda` compartida entre `usarBusquedaProductos` y
  `precargarProductos`).
- **La caché que llenó el SSR viaja al navegador**, en el `TransferState`
  (`core/consultas/transferencia-estado-consultas.ts`): `dehydrate` al
  serializar la página, `hydrate` antes de la primera navegación. Por eso el
  `resolve` de arriba no vuelve a pedir nada al hidratar: encuentra la consulta
  fresca dentro de su `staleTime`. Solo viajan las consultas en `success`; una
  que falló en el servidor la vuelve a pedir el cliente. Y el `QueryClient` es
  **uno por aplicación** —el token `CLIENTE_DE_CONSULTAS`, con fábrica—, nunca
  `new QueryClient()` suelto en `app.config.ts`: ese objeto lo compartían todos
  los renders del mismo proceso de Node.
- **El service worker no sirve el cascarón en las navegaciones.**
  `navigationRequestStrategy: "freshness"` en `ngsw-config.json`: una navegación
  va a la red y recibe el HTML del SSR; la caché solo responde sin conexión. Con
  la estrategia por omisión, la segunda visita recibía `index.csr.html` vacío y
  la tienda parecía sin productos hasta que la API respondía —encontrado en el
  ambiente desplegado, ver `docs/07-infra-gcp.md`.
- **`prefetchQuery`/`prefetchInfiniteQuery` están `@deprecated`** en favor de
  `query()`/`infiniteQuery()`, pero se usan a propósito: son los únicos que
  tragan errores (`.then(noop).catch(noop)`), así que un backend caído no
  rompe la navegación — el componente igual reintenta y muestra su error.
  - **Que no lancen no significa que terminen, y esa confusión costó un
    cuelgue.** TanStack *pausa* un fetch —sin conexión, o antes de un reintento
    con la pestaña sin foco— y una precarga pausada deja su promesa pendiente
    para siempre. El resolver que la espera para la navegación en
    `ResolveStart`: sin `ResolveEnd`, sin `NavigationCancel`, sin error, y con
    el `urlUpdateStrategy` en `deferred` **ni siquiera cambia la URL**. La
    aplicación se queda congelada en la página anterior, muda.
  - Por eso **toda precarga de un resolver pasa por `core/consultas/precarga.ts`**:
    `PRECARGA_NO_BLOQUEANTE` en las opciones y la llamada envuelta en
    `sinBloquearLaNavegacion(...)`. Hacen falta las dos. Las opciones solo valen
    si la consulta está `idle`: con un fetch ya en vuelo, `Query.fetch` devuelve
    **ese** y descarta las que le pasas — y el que está en vuelo suele ser el
    del componente en pantalla, con los valores por omisión. La que garantiza el
    arreglo es la espera acotada, que en el servidor no se aplica.
  - Y una trampa de medición encima: **si la pestaña está en segundo plano esto
    se reproduce aunque el código esté bien**, porque `canContinue()` exige
    foco antes de cada reintento. Mirar `document.visibilityState` antes de
    declarar un cuelgue.
- **Un nombre de `input()` no puede ser `id`.** Angular no lo renombra en el
  DOM: el elemento host del componente termina con el mismo `id` que el
  control interno, dos elementos con el mismo id, y `getByLabelText`
  (Testing Library) o cualquier `<label for>` apunta al host, no al control
  real. `ts-campo`/`ts-select` usan `idCampo`.
- **Una consulta que pasa de deshabilitada (`enabled: false`) a habilitada sin
  datos ya cacheados para esa llave dispara su propio fetch automático** —
  sin importar `staleTime`, porque no hay nada que considerar "fresco"
  todavía. Si justo en ese momento hay una mutación en curso escribiendo esa
  misma llave con `setQueryData`, las dos compiten y la que gane al final
  pisa a la otra — encontrado en `CarritoStore` (`agregarAlCarrito`, la
  transición ocurre exactamente cuando se crea el carrito). La corrección:
  sembrar la caché con `setQueryData` **antes** de habilitar la consulta
  (antes de fijar la señal de la que depende `enabled`/`queryKey`), para que
  nunca haya un instante de "sin datos" que dispare ese fetch. Mismo
  mecanismo de fondo que el de SSR de arriba (`PendingTasks` registrado en un
  `effect()` async): en una prueba, `fixture.whenStable()` no alcanza a esperar
  ese registro. La salida **no** es un `esperar(ms)` fijo — se espera por el
  resultado (`findBy*` si se ve en pantalla, `vi.waitFor` si no), con todas las
  aserciones dentro del mismo `waitFor`. Ver `docs/06-testing.md`.
- **La identidad de un objeto no es señal de "esto cambió".** Una revalidación
  en segundo plano (cambio de pestaña pasado el `staleTime`) devuelve un objeto
  nuevo si cambió *cualquier* cosa del producto —el precio, la existencia—, y
  todo `effect()` que dependa de esa referencia se dispara sin que haya pasado
  nada que le importe. Así el visor 360 volvía al fotograma frontal y la ficha
  perdía la variante elegida, encontrado en Fase 5 (el de la variante venía
  desde la Fase 1). **Depende del dato que de verdad define el cambio, no de la
  referencia**: `ts-visor-360` depende de una clave derivada del contenido del
  set, y la ficha del `slug` cargado, leyendo el producto con `untracked`. Si
  los datos vuelven idénticos no hace falta nada: TanStack hace *structural
  sharing* por omisión y conserva la referencia anterior.
  Con una prueba para **cada lado**: "no reiniciar en un refetch" no puede
  volverse "no reiniciar nunca" — navegar a otro producto sí tiene que soltar la
  variante y el fotograma.

## i18n con scopes perezosos

Encontrado en la vitrina del catálogo (paso de navegación previo a la Fase 5),
con los filtros mostrando `catalogo.filtros.orden.relevancia` en pantalla en vez
de "Relevancia". Que quede escrito, porque la trampa es sutil y silenciosa.

- **Nunca llames `transloco.translate()` dentro de un `computed()` ni en un
  inicializador de campo.** No lee ninguna señal, así que el `computed` se
  evalúa una sola vez y no se recalcula jamás. Si en ese instante el scope
  perezoso (`provideTranslocoScope`) todavía no ha llegado por HTTP, la clave
  cruda se queda en pantalla para siempre. Tampoco reacciona a un cambio de
  idioma: el selector navega a la misma ruta con otro prefijo y Angular reutiliza
  el componente, así que la etiqueta se queda en el idioma anterior.
- **Para una lista de etiquetas estáticas**, `translateObjectSignal('filtros.orden',
  undefined, { scope: 'catalogo' })`. Ojo: con `scope`, la clave va **relativa**
  al scope — Transloco le antepone `catalogo.` por `scopes.autoPrefixKeys`, que
  viene en `true` por defecto. Distinto de la plantilla, donde el pipe recibe la
  clave completa.
- **Para leer claves sueltas o un mapa de claves** (`CLAVE_ETIQUETA[estado]`),
  `usarTraductor()` de `core/i18n/traductor.ts`: devuelve una señal con la
  función de traducir, colgada de `events$`, que emite tanto al cargar un scope
  como al cambiar de idioma. Se usa `this.traducir()(clave)`.
- **Además, precarga el scope en el `resolve` de la ruta** con
  `precargarScopeI18n(scope)` (`core/i18n/precargar-scope.ts`), junto a las
  consultas de TanStack Query. Mismo criterio de ADR-0011 y por el mismo motivo:
  lo que la primera pantalla necesita se pide antes de crear el componente. Sin
  esa precarga, las dos herramientas de arriba igual se corrigen solas, pero el
  primer render sale con las etiquetas en blanco.
- `transloco.translate()` **sí** sirve dentro de un manejador de evento (el
  `error.set(...)` de un `onError`): para entonces el scope ya cargó. Aun así no
  reacciona a un cambio de idioma, así que el mensaje se queda como estaba.
- **Las pruebas de Vitest no reproducen este bug.** `TranslocoTestingModule` con
  `preloadLangs: true` entrega las traducciones sincrónicas, y aun con un loader
  retardado el entorno de Testing Library repinta lo suficiente como para que el
  `computed` viejo se recalcule — cosa que en el navegador hidratado no pasa.
  Verificado a mano contra `ng serve` real: los warnings *Missing translation* en
  el log del servidor son la señal fiable de que alguien volvió al patrón viejo.

## Pruebas

- Vitest para lo unitario. Funciones puras, mapeadores y stores de signals
  primero: son baratos y atrapan la mayoría de los errores.
- Componentes con Testing Library, consultas por rol y por texto accesible, nunca
  por clase CSS.
- `axe` automatizado en las pantallas clave.
- Nada de instantáneas de HTML. Nada de pruebas que solo verifican que el
  componente se construye.
