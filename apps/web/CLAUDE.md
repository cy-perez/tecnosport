# apps/web — reglas

Angular 22.5 · SSR con hidratación · standalone · zoneless con signals · SCSS ·
Transloco (es/en) · TanStack Query · Angular CDK · Vitest · npm · PWA.

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
implementación. ESLint con reglas de límites lo verifica.

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
- **Estilos:** SCSS por componente, solo con variables de `tokens.css`. Radio 0
  en todo. El chaflán se aplica con la clase `.chaflan`, nunca con
  `border-radius`.
- **Modo oscuro:** atributo `data-tema` en `<html>`, tres opciones (claro, oscuro,
  sistema), persistido y resuelto en el servidor para no parpadear al hidratar.
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
- **Imágenes:** `NgOptimizedImage` con `width` y `height`, en WebP, y `priority`
  solo en la del hero y en el fotograma frontal de la ficha.
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
- **`prefetchQuery`/`prefetchInfiniteQuery` están `@deprecated`** en favor de
  `query()`/`infiniteQuery()`, pero se usan a propósito: son los únicos que
  tragan errores (`.then(noop).catch(noop)`), así que un backend caído no
  rompe la navegación — el componente igual reintenta y muestra su error.
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
  `effect()` async): en una prueba, `fixture.whenStable()` no alcanza a
  esperar ese registro — hace falta una espera real (`esperar(ms)`, mismo
  recurso que ya usa `filtros-productos.spec.ts` para el debounce).

## Pruebas

- Vitest para lo unitario. Funciones puras, mapeadores y stores de signals
  primero: son baratos y atrapan la mayoría de los errores.
- Componentes con Testing Library, consultas por rol y por texto accesible, nunca
  por clase CSS.
- `axe` automatizado en las pantallas clave.
- Nada de instantáneas de HTML. Nada de pruebas que solo verifican que el
  componente se construye.
