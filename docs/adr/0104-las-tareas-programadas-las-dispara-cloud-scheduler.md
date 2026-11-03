# ADR-0104: las tareas programadas las dispara Cloud Scheduler

Fecha: 2026-11-03
Estado: aceptada
Reemplaza: la decisión del 23 de septiembre de 2026 de `docs/07-infra-gcp.md` para producción
—API con `min-instances = 1` y `cpu_idle = false`— y el "Cloud Scheduler queda descartado" del
mismo documento.

## Contexto

El sitio arranca con poco tráfico y la meta es que cueste lo menos posible. La pieza fija más cara
del plan de producción era la API siempre encendida con CPU asignada: unos US$45–55 al mes, los
use quien los use. Se decidió así por una sola razón: las tareas `@Scheduled` solo avanzan
mientras la instancia tiene CPU, y la conciliación de pagos no puede depender de que alguien esté
mirando la tienda.

Las tareas no son 12, como decía `docs/07`: son **17**, todas en `bootstrap/`, y cada una solo
llama a un caso de uso de `application`. Sus intervalos van de 60 segundos (resolver emisiones de
guía) a 24 horas (ocultar lo vencido de proveedores).

Guardaban en memoria cuándo corrieron por última vez. Por eso existían los `retraso-inicial-*` de
`application.yml`: cada despliegue reiniciaba la cuenta, y un vigilante de doce horas que se
redespliega cada día no corría nunca si el retraso inicial era igual a su intervalo.

## Decisión

**Un solo trabajo de Cloud Scheduler llama a `POST /api/v1/internal/tareas/tick`**, y la API
ejecuta, dentro de esa petición, cada tarea a la que ya le tocaba. Mientras la petición dura la
instancia tiene CPU; cuando responde, puede apagarse. La API de producción queda con
`min-instances = 0` y CPU solo durante la petición, igual que la de dev.

1. **Cuándo le toca a una tarea lo dice la base, no la memoria.** La tabla `ejecucion_de_tarea`
   (`V154`) guarda por tarea cuándo terminó la última vez. Le toca si pasó su intervalo desde ahí,
   que es la semántica de `fixedDelay` que ya tenían. Un reinicio no borra la cuenta, así que los
   `retraso-inicial-*` desaparecen. Un tick perdido no pierde una vuelta: el siguiente corre lo
   atrasado, una sola vez.
2. **Un arriendo y no un bloqueo de fila.** La tarea se toma con un `update` que fija
   `en_curso_hasta` solo si le toca y nadie la tiene. Se descartó `select ... for update skip
   locked` porque sostener el bloqueo pide una transacción abierta mientras la tarea corre, y los
   casos de uso se unirían a ella: la conciliación de Wompi volvería a ser un solo lote, que es lo
   que se deshizo el 4 de octubre de 2026. Con el arriendo, dos ticks cruzados o dos instancias no
   corren dos veces la misma tarea, y si la instancia muere a la mitad, el arriendo vence y la
   tarea se retoma.
3. **Una que falla no tumba a las demás**, y queda anotada como terminada: espera su intervalo,
   como hacía `@Scheduled` con una excepción. La respuesta es `200` con el resumen aunque alguna
   falle; un `5xx` haría que Cloud Scheduler repitiera el tick entero.
4. **Otra cuenta de servicio y otro rol**, `tareas-programadas` y `TAREAS_PROGRAMADAS`, separados
   de los de la ingesta (`ADR-0080`). El token de quien encola un tramo no dispara la conciliación,
   ni al revés. El verificador de tokens OIDC es el mismo, instanciado con cada cuenta.
5. **Local no cambia.** Con `tecnosport.tareas.disparador = interno` —el valor por omisión— un
   solo `@Scheduled` cada minuto llama al mismo caso de uso. Los ambientes desplegados usan
   `externo`, y dentro de la API no se programa nada.
6. **Frecuencia: cada minuto en producción, cada 30 en dev.** En producción el minuto lo piden la
   recuperación de ingestas abandonadas y la resolución de emisiones, y además mantiene caliente
   la instancia: la mayoría de las visitas no espera el arranque de la JVM. En dev, cada 30 minutos
   ensaya la cadena entera —token, rol, bitácora— sin mantener despierta a Neon, cuyo plan gratuito
   no alcanza para tener el cómputo encendido todo el mes. En dev, entonces, una tarea de un minuto
   corre cada 30: es lo que se acepta a cambio.

## Consecuencias

- La API de producción puede escalar a cero. Su costo pasa a ser casi todo de capa gratuita.
- Una visita que llega con la instancia apagada espera el arranque de la JVM. Con el tick cada
  minuto pasa poco, pero puede pasar: Cloud Run no promete cuánto tiempo conserva una instancia
  ociosa. CRaC o la imagen nativa siguen siendo el paso siguiente si eso se nota.
- Una tarea solo puede durar lo que dura una petición. Hoy ninguna se acerca: la más larga es la
  bandeja de salida, que manda hasta cien correos por vuelta.
- Lo que hagan las tareas tiene que terminar antes de que el tick responda. Un hilo que una tarea
  dejara corriendo se congelaría sin CPU, igual que la ingesta antes de `ADR-0080`.
- Una tarea nueva se registra en `CatalogoDeTareasProgramadas`, con su nombre y su intervalo. Un
  `@Scheduled` nuevo en un ambiente desplegado no correría nunca, y `ArquitecturaTest` lo impide.
