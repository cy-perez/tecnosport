# ADR-0034 — La bandeja de revisión se vacía con un acuse

Fecha: 2026-09-17
Estado: aceptado
Relacionados: `adr/0022`, `adr/0031`, `adr/0033`

## Contexto

`EstadoEnvio.exigeRevisionManual()` existe desde `adr/0022` y `EstadoEmision.exigeOjoHumano()`
desde `adr/0033`. Entre los dos nombran siete situaciones en las que un paquete se queda quieto o
queda saldo comprometido sin desenlace:

| Origen | Estados | Qué significa |
|---|---|---|
| Envío | `EXCEPCION`, `RETENIDO`, `CANCELADO`, `DESTRUIDO`, `FALLIDO` | El paquete no se mueve y no va a moverse solo |
| Emisión | `INDETERMINADA` | Se pidió, la llamada no terminó, **pudo cobrar** |
| Emisión | `PARCIAL` | Se cobró, y hay guías vivas sin usar |

**Ninguno de los dos predicados lo llamaba nada en producción.** Solo sus propias pruebas. El
javadoc de `exigeRevisionManual()` lo decía con todas las letras —"a día de hoy nadie los mira"— y
así estuvo dos fases: los estados se calculaban, se guardaban, y aparecían en un `warn` del
registro, que es tanto como no aparecer.

Un método que solo se prueba a sí mismo parece cubierto y no cubre nada. Es el mismo patrón que
`adr/0033` encontró en la revisión adversarial: una garantía afirmada en un documento que el código
no cumple.

## Decisión

### 1. La consulta acota y el dominio decide

El repositorio trae los envíos que **probablemente** tienen una guía quieta: un `exists` sobre "no
hay ningún evento posterior a este". Quién pide ojo humano de verdad lo dice `GuiaEnvio.ultimoEstado()`
sobre el agregado ya cargado.

El filtro de la base trae de más y nunca de menos —dos eventos de la misma guía con el mismo
instante hacen que "el último" no sea uno solo desde el punto de vista de una consulta— y el caso de
uso descarta lo que sobra.

**La alternativa era escribir la regla entera en SQL**, y se descartó: habría dos definiciones de
"último estado" capaces de divergir, y de esas dos solo una tiene pruebas.

### 2. Hay un acuse, y por eso la bandeja se puede vaciar

`AcuseDeRevision` guarda quién miró, cuándo y qué concluyó.

Existe porque **dos de los cinco estados de envío son además terminales**: de una guía `CANCELADO`
o `DESTRUIDO` no llega otro evento nunca. Una bandeja calculada solo a partir del estado las
acumularía para siempre, y a los pocos meses sería una lista que nadie abre. Una bandeja que no se
puede vaciar deja de ser una bandeja, y entonces volvemos al `warn` del registro con más pasos.

Se consideró y se descartó una bandeja puramente derivada con una ventana de tiempo —"solo lo de
los últimos N días"—: el criterio es arbitrario y un caso caro desaparecería solo, sin que nadie lo
hubiera visto.

### 3. La regla que devuelve una guía a la bandeja vive en el caso de uso, no en la consulta

Una guía acusada vuelve a aparecer si le llega un evento **posterior** al acuse. Sin esa regla,
acusar sería una mordaza: lo que se revisó fue el estado de antes, no este.

La comparación es contra `EventoSeguimiento.recibidoEn` —cuándo nos enteramos— y **nunca** contra
`ocurrioEn`, que lo pone la transportadora. Son dos relojes distintos: un evento fechado con desfase
parecería anterior al acuse sin serlo, y el precio de equivocarse es una guía en excepción que
desaparece de la vista sin que nadie la haya visto.

Está en el caso de uso y no en un `not exists` de la consulta porque compara dos instantes de dos
tablas, que es exactamente el tipo de cosa que hay que poder probar sin base de datos.

### 4. El acuse **no** resuelve nada

Acusar una emisión `INDETERMINADA` no la pasa a `FALLIDA` y no desbloquea el pedido: sigue abierta y
sigue impidiendo una emisión nueva, que es justo lo que evita pagar dos veces por lo mismo.

Decidir que no hubo cobro es mover plata, y tiene **su propia puerta**: la decisión 5. Que sean dos
acciones y no una es el punto — mirar y dejar constancia no puede ser lo mismo que afirmar qué pasó
con un cobro. Quien tenga prisa despacha a mano, que es la salida que ya existe en esa misma
pantalla.

**La pantalla lo dice con esas palabras**, no solo el código: quien marca la fila se puede ir
creyendo que desbloqueó el pedido.

Acusar algo que no está pidiendo revisión responde 409. Se rechaza en vez de guardarlo igual porque
un acuse sobre algo sano deja escrito que ahí hubo un problema que nunca existió, y eso ensucia el
rastro que la tabla existe para dar.

### 5. Una emisión indeterminada la resuelve quien miró el panel, y el sistema no adivina

Acusar una `INDETERMINADA` la sacaba de la bandeja y dejaba el pedido bloqueado igual: el índice
único de emisiones abiertas impide una emisión nueva, y con razón —encima de una que pudo cobrar
sería pagar dos veces—.

**Se consideró resolverlo solo** y se descartó. La idea era reenviar `POST /shipments` con el mismo
`idTarifa` y dejar que la caché de idempotencia de la plataforma —`unique_shipment`, 96 horas por
`rate_id`— devolviera el envío si ya existía. Dos motivos para no hacerlo:

- Esa caché está **documentada por el proveedor y no medida por nosotros**
  (`docs/13-skydropx-capacidades.md` §6.2). Este proveedor ya cobró cuatro veces el precio de
  creerle a su documentación, y la quinta fue el mismo día de este ADR: `§6.11`.
- Fuera de esa ventana el reenvío **crearía un segundo envío pagado**, que es exactamente el
  desastre que `adr/0033` existe para evitar.

Quien resuelve está mirando el panel de la plataforma: **ve** si el envío está. Registrar lo que vio
no necesita ninguna suposición. Dos salidas, las dos solo desde `INDETERMINADA`:

| Lo que vio | Estado | Efecto |
|---|---|---|
| El envío no está | `FALLIDA` | El pedido queda libre para emitir otra guía |
| El envío está, y es este | `EN_CURSO` | La tarea de resolución de siempre lo relee y despacha |

Vuelve a `EN_CURSO` y no a `EMITIDA`, aunque en el panel ya se vea un número de guía: que el
desenlace lo escriba la plataforma al releer —y no lo que alguien tecleó— es lo que impide que un
número mal copiado acabe impreso en una etiqueta y en un correo al comprador.

### 6. Hay un vigilante, porque que la pantalla exista no hace que alguien la abra

Un aviso al negocio cuando algo lleva más de **veinticuatro horas** en la bandeja sin que nadie lo
toque. El umbral es un dato de negocio, no una constante técnica: el comprador de un pedido
despachado espera movimiento diario, y enterarse después que él es justo lo que la bandeja vino a
evitar.

**Avisar no es revisar**, y por eso el aviso vive en su propia tabla y no como un acuse: un acuse
del sistema vaciaría la bandeja sin que nadie hubiera mirado nada, que es el defecto que este ADR
entero vino a corregir. Lo que está quieto sigue quieto y sigue en la bandeja.

El reclamo del aviso es una sola escritura condicional y atómica, mismo criterio que
`RepositorioPedidos.reclamarAvisoDePlazo`, y se vuelve a armar con una novedad posterior —con la
misma regla de la decisión 3—: un paquete que empeora no puede pasar callado porque ya se avisó de
su estado anterior.

## Consecuencias

- Los dos predicados tienen por fin quien los llame. El javadoc que decía que nadie los mira se
  corrigió en el mismo commit que dejó de ser cierto.
- `GuiaEnvio.ultimoEvento()` se añadió al dominio: la bandeja necesita el evento entero —la
  descripción y las dos fechas—, no solo su estado.
- **Dos tablas nuevas, y ninguna compartida**: `acuse_revision` dice quién miró —append-only, con
  dos columnas de referencia excluyentes por restricción de la base para no perder la llave
  foránea— y `aviso_revision` dice de qué se avisó. Se parecen y no son lo mismo: juntarlas haría
  que avisar contara como revisar.
- `EmisionDeGuia` gana la única transición que **reabre** algo: `recuperada` la devuelve de
  `INDETERMINADA` a `EN_CURSO` y limpia su `resueltaEn`, porque no estaba resuelta.
- **Queda abierto**: nada mide cuánto tarda el negocio en atender lo que la bandeja muestra. El
  aviso dice que algo lleva un día esperando; no dice si el correo sirvió de algo. Se sabrá cuando
  haya casos reales.
- **Queda abierto y no es nuestro**: la recolección por API sigue caída del lado de la
  transportadora (`docs/13` §6.11), así que un paquete detenido se sigue resolviendo por teléfono.
