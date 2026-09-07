# TecnoSport — contexto del proyecto

Ecommerce propio de TecnoSport (tecnosport.co): ropa y calzado deportivo, bolsos
y celulares. Venta al detal, pago en línea y contraentrega, envío nacional.
Medellín, Colombia. Un solo desarrollador. El sitio web es la fase 1; la app
móvil viene después y por eso el backend nunca asume que su único cliente es la
web.

Monorepo: `apps/api`, `apps/web`, `packages/`, `infra/`.

**Antes de escribir código, lee `docs/00-producto.md` y `docs/01-arquitectura.md`.**
El índice completo está en `docs/README.md`.

---

## Reglas duras

Estas no se negocian en una conversación. Si una tarea parece exigir romper una,
para y dime por qué antes de escribir el código.

1. **La dirección de las dependencias no se invierte nunca.**
   `presentation → application → domain` e `infrastructure → application → domain`.
   `domain` no importa nada de Spring, de JPA ni de Jackson. ArchUnit lo verifica
   y el build falla si se rompe. En el frontend lo verifica
   **`npm run capas`**, y hay una historia detrás que conviene conocer:
   `eslint-plugin-boundaries` estaba configurado con la sintaxis legada y el
   plugin v7 **la acepta sin aplicarla** — se comprobó metiendo violaciones a
   propósito y el lint pasaba. Un guardián que nunca dispara da confianza falsa.
   `tools/verificar-capas.mjs` aplica las mismas cuatro reglas sobre los imports
   relativos, en 90 líneas y sin dependencias.
   Dos excepciones, las dos porque el enunciado de la regla las pide:
   `*.routes.ts` sí puede importar `infrastructure` —es el proveedor de la ruta
   el que elige la implementación— y los `*.spec.ts` se informan aparte sin
   fallar, porque montar un escenario no es desplegar código.
   **No queda ninguna violación en producción**, y por eso `npm run capas` ya
   corre dentro de `npm run verificar` — es el primer paso, antes del lint. Las
   tres que hubo estaban en el carrito, leyendo `localStorage` desde
   `application` y `presentation`; se resolvieron con dos puertos
   (`AlmacenCarritoId`, `AlmacenSnapshotLineas`) provistos en `app.config.ts`.
   Un guardián que no está enganchado al build es un guardián opcional.
2. **Ningún HEX, ningún píxel suelto, ninguna fuente literal en el frontend.**
   Todo sale de `packages/marca/tokens.css`. Si falta un valor, el sistema está
   incompleto: se añade al `tokens.json` del kit y se regenera.
   Desde `ADR-0020` la vía es una utilidad de Tailwind mapeada a un token
   (`bg-ts-primario`, `p-16`), no SCSS a mano. Las escalas por omisión de
   Tailwind están borradas, así que `bg-red-500` y `rounded-lg` **no existen**.
   Ojo: `rounded-full` y los valores arbitrarios sí sobreviven; ahí "radio 0 en
   todo" lo sostiene la regla, no el compilador.
   La única escapatoria es `h-[var(--token)]`; `h-[72px]` no.
   Quedan dos literales, los dos justificados y documentados en
   `apps/web/src/tailwind.css`: los puntos de quiebre, porque una media query no
   puede leer una propiedad personalizada de CSS, y los 44 px de objetivo táctil,
   que `tokens.json` no define.
3. **No se edita `tokens.css` ni `fuentes.css` a mano.** Son generados. Tailwind
   los *consume*; no los reemplaza ni los reescribe.
4. **Ningún texto visible escrito directo en una plantilla.** Todo pasa por
   Transloco, en español e inglés, incluidos los mensajes de error y los
   `aria-label`.
5. **Ninguna URL, credencial, clave ni endpoint literal en el código.**
   Configuración por variables de entorno, tipada y validada al arrancar.
6. **El dinero nunca es `double` ni `float`.** Ver `docs/02-modelo-datos.md`.
7. **El servidor no confía en el cliente** para precio, existencia, costo de
   envío ni estado de pago. Nunca.
8. **Nada se da por terminado sin pruebas** que fallen si la lógica se rompe.
   En el frontend hay dos cosas que las pruebas **no** atrapan y hay que
   verificar en el navegador: que una clase de Tailwind exista de verdad (una
   inventada no falla, no hace nada) y el foco — `:focus-visible` y la trampa de
   foco del CDK no se reproducen en jsdom. Ver `docs/06-testing.md`.
9. **No inventes la API de una versión.** Java 21, Spring Boot 4.1.0 y Angular
   22.5 son recientes. Si no estás seguro de una firma, una anotación o un
   builder, dilo y consúltalo. Una alucinación de API cuesta más que una pregunta.

## Idioma

- **Dominio y aplicación: español.** `Producto`, `Pedido`, `LineaPedido`,
  `ConfirmarPedido`, `RepositorioPedidos`. Sin espanglish.
- **Infraestructura y frameworks: inglés**, porque hereda del framework:
  `PedidoJpaRepository`, `WompiClient`, `SecurityConfig`.
- **Commits, PR, comentarios y documentación: español.**
- **Textos de interfaz: nunca en el código.** Van en los JSON de Transloco.

## Comandos

```
npm run verificar                        lint + pruebas + build de todo
npm run dev --workspace=apps/web         frontend en :4200
npm test --workspace=apps/web            Vitest
npm run contratos                        regenera el cliente desde el OpenAPI
npm run clases -- <clase>...             ¿esa clase de Tailwind existe de verdad?
npm run contrastes                       WCAG AA de los pares de color, claro y oscuro
npm run capas                            ¿alguna dependencia invertida en el frontend?
docker compose up -d                     PostgreSQL, Mailpit, Adminer

cd apps/api
gradlew.bat build                        compila, prueba y valida arquitectura
gradlew.bat bootRun                      API en :8080
gradlew.bat spotlessApply                formato
```

Windows: `gradlew.bat`, rutas con `/` en configuración, scripts en Node o Gradle.

## Convenciones de git

- Ramas: `feat/`, `fix/`, `refactor/`, `docs/`, `chore/` más descripción corta.
- Commits convencionales en español, con el alcance del monorepo:
  `feat(api/catalogo): reservar inventario al iniciar el pago`
  `feat(web/visor360): arrastre continuo en escritorio`
- Un commit por unidad coherente. Nada de commits que mezclan una migración, un
  componente y un cambio de estilos.
- Nunca `--force` sobre `main`. Nunca commits con secretos, ni de prueba.

## Cómo quiero que trabajes conmigo

- **Plan antes de código** en cualquier tarea que toque más de tres archivos.
  Escríbelo, espera que lo apruebe, y solo entonces edita.
- **Un caso de uso a la vez.** No generes el módulo de pedidos completo de una
  sentada: caso de uso, prueba, adaptador, y para.
- **Cuando algo del diseño te parezca mal, dilo.** Soy nivel intermedio en
  Angular y en Spring: prefiero discutir una decisión a que implementes en
  silencio algo que sabes que se va a caer.
- **No agregues dependencias sin preguntar.** Cada librería nueva es deuda.
- **No inventes datos de negocio.** Si falta una tarifa, un porcentaje o un
  plazo, déjalo como `TODO` con el nombre del dato y pregúntame.
- **Una sola conversación para frontend y backend.** Al ser un monorepo, trabajo
  siempre desde la raíz del repo (nunca abras Claude Code parado dentro de
  `apps/api` o `apps/web` por separado), así los cambios de ambos lados quedan
  en el mismo hilo. Para retomar esta conversación en vez de abrir una nueva,
  arranca con `claude --continue` (o `claude -c`); si hay varias, `claude
  --resume` (o `/resume`) deja elegir cuál.
