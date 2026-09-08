# ADR 0020. Tailwind sobre los tokens de marca, no en lugar de ellos

Fecha: 2026-09-07. Estado: aceptada.

## Contexto

El sistema visual llegó cerrado desde dos kits externos y se vendoriza en
`packages/marca` (`docs/04-ui-marca.md`). Hasta la Fase 5 se consumía con SCSS
por componente, solo con variables de `tokens.css`, y funcionaba: 1.972 líneas
de SCSS en 45 plantillas, consistentes en claro y en oscuro.

Se pidió integrar un stack de UI headless: Tailwind CSS, Angular CDK, Spartan
UI, `lucide-angular` y `@angular/animations`. La petición choca de frente con
tres cosas escritas:

- **Regla dura #2**, "ningún HEX, ningún píxel suelto": las utilidades de
  Tailwind por omisión (`p-4`, `bg-slate-800`, `rounded-lg`) *son* valores
  sueltos con otro nombre, y una paleta en la configuración de Tailwind sería
  una segunda fuente de verdad frente a `tokens.json`.
- **"Radio 0 en todo"** y el chaflán a 45 grados como firma de la marca. Spartan
  UI trae la estética redondeada de shadcn y su propio juego de tokens.
- **La decisión de iconografía**, fechada en la Fase 4: "SVG propio, en línea.
  Sin librería de iconos", con el umbral para revisarla en "más de seis u ocho".
  Había uno.

La alternativa —no integrar nada— se planteó y se descartó explícitamente: se
autorizó romper la regla #2 y revertir la decisión de iconografía.

## Decisión

Tailwind entra, pero **como consumidor de los tokens, no como sistema de diseño
paralelo**. Cinco piezas, todas en `apps/web/src/tailwind.css`:

1. **Las escalas por omisión se borran** (`--color-*: initial`, `--spacing`,
   `--radius-*`, `--font-*`, `--text-*`, `--shadow-*`, `--breakpoint-*`). Lo que
   no existe no se puede usar: `bg-red-500`, `p-7`, `text-8xl` y
   `rounded-sm/md/lg/xl` no compilan.

   **Con un límite que hay que conocer, porque se documentó mal al principio:**
   borrar la escala **no** elimina todas las utilidades de radio. Lo que muere
   es la escala nombrada (`rounded-sm/md/lg/xl`) y las esquinas a secas
   (`rounded-t`, `rounded-s`), que dependen de ella. Siguen existiendo
   `rounded`, `rounded-none`, `rounded-full`, cualquier valor arbitrario
   (`rounded-[8px]`) y las esquinas combinadas con esos tres
   (`rounded-t-full`, `rounded-tl-none`, `rounded-t-[8px]`), porque no salen de
   `--radius-*` sino de valores estáticos de Tailwind. Así que "radio 0
   en todo" queda impuesto por el compilador **en el camino normal** y por la
   regla en el resto. Para auditarlo: `grep -rn "rounded-" apps/web/src`.
   Comprobado con `npm run clases`, clase por clase — la redacción anterior daba
   por buenas las variantes por esquina sin medirlas.
2. **El tema se redefine con `@theme inline` apuntando a los tokens.**
   `--color-ts-primario: var(--color-primario)` hace que `bg-ts-primario`
   compile a `background-color: var(--color-primario)`. El modificador `inline`
   es la pieza clave: deja la `var()` en la utilidad en vez de una copia del
   valor.
3. **El modo oscuro no usa `.dark`.** El variant propio `oscuro:` se cuelga del
   `data-tema` que ya existía, resuelto en el servidor durante el SSR. En la
   práctica casi no se usa: como los tokens se redefinen bajo
   `[data-tema="oscuro"]` y las utilidades apuntan a la `var()`, **el tema
   cambia sin un solo `oscuro:` en los componentes** — verificado en el
   navegador.
4. **Preflight queda fuera.** Resetea márgenes, rellenos y tipografía de todo el
   documento, y habría cambiado el aspecto de 45 plantillas de golpe, que es lo
   contrario de una refactorización incremental. Se rescata solo su línea de
   bordes, sin la cual la utilidad `border` no dibuja nada.
5. **`cn()` (`shared/ui/cn.ts`)** resuelve conflictos de clases con
   `tailwind-merge`, configurado con los nombres de token del proyecto.

Sobre el resto del stack pedido:

- **Angular CDK**: ya estaba instalado y sin usar. Su primer uso real es
  `shared/ui/dialogo`.
- **Spartan UI**: solo `@spartan-ng/brain` (las primitivas headless, compatible
  con Angular 22). **No** `@spartan-ng/cli`, que arrastra todo Nx y
  `@schematics/angular@21` a un monorepo de npm workspaces sin Nx. La capa
  estilada se escribe a mano en `shared/ui/`.

  **Corrección posterior.** Al principio se escribieron todos los componentes a
  mano contra el CDK y `brain` quedó instalado sin una sola importación: una
  dependencia muerta, que es peor que cualquiera de las dos decisiones. Se
  cerró reconstruyendo `ts-dialogo` sobre sus primitivas —que aportan portal
  del CDK, bloqueo de desplazamiento y cableado de `aria-labelledby`, tres
  cosas que la versión a mano no tenía— y desinstalando `tw-animate-css`, que
  había entrado como peer suyo y tampoco se usaba. `clsx` pasó a declararse
  explícito: se importaba en `cn.ts` viniendo solo como peer transitivo.
- **Iconos**: el paquete `lucide` (los datos: `[etiqueta, atributos][]`), no
  `lucide-angular`. Ese declara `@angular/core: 13.x - 21.x` y el proyecto va en
  la 22: instalarlo exigiría `--legacy-peer-deps` para todo el monorepo. `lucide`
  no declara ningún peer.
- **`@angular/animations`: no se instala.** El paquete está **deprecado** —npm lo
  anuncia al instalarlo— en favor de `animate.enter` y `animate.leave`, nativos
  en el core de la 22 (verificado en `core.d.ts`). No hay nada que proveer en
  `app.config.ts`.

## Alternativas descartadas

**Tailwind con su propia paleta, sincronizada a mano con `tokens.json`.** Es lo
que se pidió literalmente. Descartada porque duplica la fuente de verdad de 97
variables que ya existen, y `tokens.css` es generado: la copia divergiría en la
primera entrega nueva del kit.

**Conservar las escalas por omisión y confiar en la disciplina.** Descartada
porque la disciplina no es verificable. Borrarlas convierte "no uses `rounded-lg`"
en "`rounded-lg` no existe".

**`lucide-angular` con `overrides` en el `package.json` raíz** para relajar solo
ese peer. Más quirúrgico que `--legacy-peer-deps`, pero sigue pasando por encima
de una restricción que puso el mantenedor, y `lucide` da el mismo set de iconos
—más nuevo— sin acoplamiento de versión.

**Un resolutor de conflictos de clases propio**, para ahorrar los 27,70 kB de
`tailwind-merge`. Se midió y se ofreció; se decidió conservar la librería y subir
el techo de presupuesto de 600 a 700 kB.

## Consecuencias

- **La regla dura #2 cambia de forma, no de fondo.** Sigue prohibido escribir un
  HEX o un píxel suelto, pero ahora la vía es una utilidad de Tailwind mapeada a
  un token. La escapatoria permitida es `h-[var(--token)]`; `h-[72px]` no.
- **Quedan dos valores literales, los dos documentados en el archivo.** Los
  puntos de quiebre, porque **una media query no puede leer una propiedad
  personalizada de CSS** (el SCSS ya los escribía literales por lo mismo), y
  `--spacing-tactil: 44px`, el objetivo táctil que la tabla de medidas de
  `docs/04-ui-marca.md` exige y `tokens.json` **no define**. Hay un `TODO` para
  pedirlo al kit.
- **Riesgo nuevo, y es el precio de haber borrado las escalas: una clase que no
  existe no falla, simplemente no hace nada.** Ocurrió dos veces en la propia
  fase (`min-h-0`, `min-h-auto`). No hay herramienta que avise: **hay que leer el
  CSS compilado**. Ver `docs/06-testing.md`.
- **`cn()` hay que mantenerlo al día.** `tailwind-merge` no conoce nuestro
  vocabulario y falla de dos maneras: fusionó `font-texto` con `font-medio`
  —creyendo que `medio` era una familia— y **el botón se pintaba en Arial**; y no
  fusiona `leading-*`, `max-w-*` ni `min-h-tactil`, dejando que gane el orden del
  CSS. Los grupos están declarados en `cn.ts` con pruebas de regresión. Al añadir
  un token con nombre no numérico hay que registrarlo ahí.
- **El bundle inicial sube de 603,70 a 642,84 kB**, de los cuales 27,70 kB son
  `tailwind-merge` y 8,41 kB todo lo demás (el CSS, `clsx`, el icono, los cinco
  componentes). El techo de aviso pasa a 700 kB. El presupuesto **ya se incumplía
  por 3,70 kB antes de esta fase**: la conversación de bundle sigue pendiente.
- Angular queda en la 22. Bajar a la 21 —lo único que habría contentado el peer
  de `lucide-angular`— arrastraría un downgrade de TypeScript, porque la 21 exige
  `>=5.9 <6.0` y la 22.1.4 exige `>=6.0 <6.1`.
