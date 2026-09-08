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
| Extremo a extremo | Spring Boot Test con Wompi y Skydropx falsos | Los recorridos de `00-producto.md` |

Nunca H2. Si la prueba no corre contra el mismo motor que producción, no prueba
la consulta que importa.

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
una clase verificable con `npm run clases`.

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

### Lo que sigue sin herramienta

**Lighthouse no está automatizado.** Se corre a mano desde DevTools sobre un
`ng build` servido en producción, no sobre `ng serve`, que no comprime y ensucia
los números.
