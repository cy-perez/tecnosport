# ADR-0055 — El contrato se guarda, y se vigila en el build

**Fecha:** 2026-09-21
**Estado:** aceptado. Reemplaza el trabajo `contrato` de integración continua, que se retira.

## Contexto

El cliente TypeScript de `packages/contratos` se genera desde el OpenAPI que sirve el backend.
Nada en el repositorio garantizaba que siguiera correspondiéndole: `tipos.ts` se regeneraba a mano
con `npm run contratos`, contra `http://localhost:8080`.

Que eso importa está medido dos veces:

- **Fase 7.** La migración del paquete por variante agregó cuatro campos. El frontend los mandaba,
  `tipos.ts` no los tenía, y *"ni el lint ni el build ni las 748 pruebas dijeron nada"*.
- **21 de septiembre de 2026.** springdoc publicaba `disponible` como opcional —no deduce que un
  `boolean` primitivo siempre se serializa—, el cliente lo generaba como `disponible?: boolean` y
  el mapeador del frontend caía a `?? false`. El día que ese campo dejara de serializarse, la
  tienda entera saldría agotada: todos los botones de comprar deshabilitados, sin una prueba en
  rojo y sin una línea en el registro.

Había un guardián, y esa parte se enunció mal al listarlo como deuda: el trabajo `contrato` de
`verificar.yml` levantaba PostgreSQL, arrancaba `bootRun`, esperaba hasta cinco minutos a que
respondiera, regeneraba el cliente y exigía que el diff quedara vacío. Funcionaba. Atrapó por lo
menos una vez lo que tenía que atrapar.

**El problema es dónde vivía.** Avisaba después del empujón y ya sobre la rama, mientras
`npm run verificar` pasaba en verde en la máquina de quien programa con el cliente desactualizado.
Este repositorio ya tiene esa lección escrita, en el javadoc de `ContextoBajoPerfilE2eTest`: *"el
único guardián era el flujo de integración continua, seis minutos después del merge y ya sobre
`main`"*.

Y vivía ahí por una razón concreta: **el contrato solo existía como respuesta de un servidor
vivo**, así que cualquier comprobación necesitaba levantar uno.

## Decisión

**El OpenAPI se guarda en el repositorio**, en `packages/contratos/openapi.json`, y la cadena se
parte en dos eslabones con un guardián cada uno:

| Qué se vigila | Quién | Dónde corre | Qué necesita |
|---|---|---|---|
| Que la instantánea sea lo que la aplicación sirve | `ContratoOpenApiTest` | `gradlew build` | Docker, como el resto de `bootstrap` |
| Que `tipos.ts` corresponda a la instantánea | `tools/verificar-contratos.mjs` | `npm run verificar` | Nada |

Mover el contrato a propósito son dos pasos, y los tres archivos van en el mismo commit:

```
cd apps/api && gradlew.bat :bootstrap:test --tests "*ContratoOpenApiTest" -PactualizarContrato=true
npm run contratos
```

### Tres detalles que no son de estilo

**MockMvc y no un puerto de verdad.** Con `webEnvironment = RANDOM_PORT`, springdoc escribe en
`servers` la URL por la que le llegó la petición, con el puerto aleatorio dentro: la instantánea
cambiaría en cada corrida. Aun con MockMvc, `servers` se quita al normalizar — describe dónde está
desplegada la API, no qué contrato tiene.

**Las llaves se ordenan.** Nada obliga a springdoc a serializar los caminos y los esquemas en un
orden estable entre versiones. Un guardián que falla porque dos llaves cambiaron de sitio no dice
nada del contrato, y a la tercera vez se desactiva.

**Los saltos de línea son de Unix, explícitamente.** `DefaultPrettyPrinter` usa por omisión el
separador del sistema: sin fijarlo, la misma aplicación escribiría CRLF en Windows y LF en
integración continua, y el guardián fallaría según en qué máquina corriera. Es el error que
`verificar-kit.mjs` ya había pagado tres días antes.

## Alternativas descartadas

**El plugin de Gradle de springdoc** (`org.springdoc.openapi-gradle-plugin`), que genera el JSON
durante el build. Es la respuesta obvia y se descartó por dos hechos comprobados en la fuente, no
de memoria (regla dura #9): su última versión es la **1.9.0, de junio de 2024**, y no declara nada
sobre Spring Boot 4; y funciona **arrancando la aplicación entera** en un proceso aparte, que aquí
exige PostgreSQL y la configuración validada — o sea, el mismo costo que ya tenía el trabajo de
integración continua, más una dependencia de build que nadie más usa.

**Dejar el trabajo de integración continua y añadir solo la comprobación local.** Se descartó
porque después de este cambio no comprueba nada que los dos eslabones no comprueben antes, y
veinte minutos de ejecutor por una redundancia enseñan a ignorar el trabajo rojo.

**Un guardián local que se salte cuando la API no responda.** Es lo que se habría necesitado sin
la instantánea, y es exactamente la forma que la regla dura #1 documenta como falsa confianza: el
`eslint-plugin-boundaries` que aceptaba la configuración sin aplicarla. Un guardián opcional es el
que no vigila.

## Consecuencias

- **Un archivo generado más en el repositorio**, y con él la obligación de regenerarlo: si alguien
  cambia un DTO y no lo hace, el build falla en su máquina. Ese es el punto.
- **El primer regenerado movió 1.455 de las 4.190 líneas de `tipos.ts`** y ninguna era un cambio
  de contrato: ordenadas, las dos versiones son idénticas línea por línea. Comprobarlo importaba
  más que el diff — una sola diferencia real habría significado que el cliente llevaba días
  mintiéndole al frontend.
- **`npm run contratos` ya no necesita el backend levantado**, así que regenerar el cliente deja
  de depender de Docker y de la base.
- **Queda un hueco, y conviene nombrarlo:** la instantánea se actualiza con una bandera, y una
  bandera se puede correr sin mirar lo que cambió. Lo que el guardián garantiza es que el cambio
  sea **visible en el diff de un commit**, no que alguien lo haya pensado. Para eso está la
  revisión.
