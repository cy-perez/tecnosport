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
| Extremo a extremo | Spring Boot Test con Wompi falso | Los cinco recorridos de `00-producto.md` |

Nunca H2. Si la prueba no corre contra el mismo motor que producción, no prueba
la consulta que importa.

Lo que tiene prueba sin excepción:

- Cálculo de total con IVA y redondeo.
- Reserva de inventario con dos compradores simultáneos por la última unidad.
- Vencimiento de reserva a los 30 minutos con `Reloj` falso.
- Reserva de contraentrega que **no** vence por tiempo.
- Disponibilidad de contraentrega: fuera de cobertura, sobre el monto máximo, y
  comprador con rechazo previo. Los tres deben excluir el método.
- Webhook de Wompi repetido: no cobra ni descuenta dos veces.
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
| Accesibilidad | `axe` en las pantallas clave |
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

Nada de instantáneas de HTML: se rompen con cualquier cambio de estilo y no dicen
nada. Nada de pruebas que solo verifican que el componente se construye. Nada de
consultas por clase CSS.

## En integración continua

En cada pull request: compilar, lint, pruebas de las dos aplicaciones, ArchUnit,
verificación de que `es.json` y `en.json` tienen las mismas claves, y
`terraform plan`. Si algo de eso falla, no se mezcla.

Cobertura: se mide, no se persigue. Un noventa por ciento con pruebas triviales es
peor que un sesenta donde lo cubierto es dinero, inventario y pagos.
