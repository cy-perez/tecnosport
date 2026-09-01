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
- **Estilos:** SCSS por componente, solo con variables de `tokens.css`. Radio 0
  en todo. El chaflán se aplica con la clase `.chaflan`, nunca con
  `border-radius`.
- **Modo oscuro:** atributo `data-tema` en `<html>`, tres opciones (claro, oscuro,
  sistema), persistido y resuelto en el servidor para no parpadear al hidratar.
- **Accesibilidad no es una fase final.** Todo control alcanzable por teclado, el
  anillo de foco no se elimina jamás, diálogos con el CDK y trampa de foco,
  imágenes con `alt` traducido, `label` real en cada campo.
- **Imágenes:** `NgOptimizedImage` con `width` y `height`, en WebP, y `priority`
  solo en la del hero y en el fotograma frontal de la ficha.
- **SSR:** nada de `window`, `document`, `localStorage`, `navigator` ni sensores
  fuera de un guardia de plataforma. Las consultas de la primera pantalla se
  prehidratan.
- **Rutas:** `loadComponent` o `loadChildren` en todo. Nada eager salvo el
  layout. `/admin` y `captura360` en sus propios bundles.
- **Formularios reactivos y tipados.** Los mensajes de error salen de Transloco.
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

## Pruebas

- Vitest para lo unitario. Funciones puras, mapeadores y stores de signals
  primero: son baratos y atrapan la mayoría de los errores.
- Componentes con Testing Library, consultas por rol y por texto accesible, nunca
  por clase CSS.
- `axe` automatizado en las pantallas clave.
- Nada de instantáneas de HTML. Nada de pruebas que solo verifican que el
  componente se construye.
