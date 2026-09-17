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

Decidir que no hubo cobro es mover plata, y necesita su propia puerta con su propia comprobación
contra la plataforma. Quien tenga prisa despacha a mano, que es la salida que ya existe en esa misma
pantalla.

**La pantalla lo dice con esas palabras**, no solo el código: quien marca la fila se puede ir
creyendo que desbloqueó el pedido.

Acusar algo que no está pidiendo revisión responde 409. Se rechaza en vez de guardarlo igual porque
un acuse sobre algo sano deja escrito que ahí hubo un problema que nunca existió, y eso ensucia el
rastro que la tabla existe para dar.

## Consecuencias

- Los dos predicados tienen por fin quien los llame. El javadoc que decía que nadie los mira se
  corrigió en el mismo commit que dejó de ser cierto.
- Una tabla nueva, `acuse_revision`, append-only y con dos columnas de referencia excluyentes por
  restricción de la base para no perder la llave foránea.
- `GuiaEnvio.ultimoEvento()` se añadió al dominio: la bandeja necesita el evento entero —la
  descripción y las dos fechas—, no solo su estado.
- **Queda abierto**: resolver una `INDETERMINADA` sigue sin endpoint. Es lo siguiente, y mueve
  plata.
- **Queda abierto**: nadie vigila la bandeja. Que exista la pantalla no hace que alguien la abra;
  un aviso cuando algo lleva demasiado tiempo sin acusar es otra decisión, del tamaño del vigilante
  del plazo de entrega (`adr/0028`).
