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
   y el build falla si se rompe. En el frontend, lo mismo, verificado con ESLint.
2. **Ningún HEX, ningún píxel suelto, ninguna fuente literal en el frontend.**
   Todo sale de `packages/marca/tokens.css`. Si falta un valor, el sistema está
   incompleto: se añade al `tokens.json` del kit y se regenera.
3. **No se edita `tokens.css` ni `fuentes.css` a mano.** Son generados.
4. **Ningún texto visible escrito directo en una plantilla.** Todo pasa por
   Transloco, en español e inglés, incluidos los mensajes de error y los
   `aria-label`.
5. **Ninguna URL, credencial, clave ni endpoint literal en el código.**
   Configuración por variables de entorno, tipada y validada al arrancar.
6. **El dinero nunca es `double` ni `float`.** Ver `docs/02-modelo-datos.md`.
7. **El servidor no confía en el cliente** para precio, existencia, costo de
   envío ni estado de pago. Nunca.
8. **Nada se da por terminado sin pruebas** que fallen si la lógica se rompe.
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
