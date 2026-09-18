# ADR-0043 — El recaudo registra por dónde entró, no elige por dónde entra

Fecha: 2026-09-18
Estado: aceptado
Relacionados: `adr/0037`, `docs/13-skydropx-capacidades.md` §3 y §5 (decisión 6),
`docs/11-pagos-y-envios.md`

## Contexto

La decisión 6 de `docs/13` §5 llevaba abierta desde el 14 de septiembre: *"dónde cae el recaudo:
créditos sin comisión o banco con comisión los jueves. Es una decisión contable, no técnica."*

Preguntado al negocio el 18 de septiembre, la respuesta fue que **la cuenta tiene las dos**, y la
petición: poder elegir manualmente, envío por envío, qué hacer con cada contraentrega.

## Lo que se midió antes de construirlo, y cambió la forma

La petición, tal como estaba enunciada, **no se puede cumplir**: las dos modalidades son formas de
**retirar el saldo acumulado**, no un campo del envío (`docs/13` §3). El cuerpo de la guía no lleva
nada que diga dónde cae el dinero —`on_delivery_amount` lleva el monto y `on_delivery_status` el
estado, y se acabó—, y la elección se hace en el panel de Skydropx al retirar.

Una pantalla nuestra que "eligiera" por envío no le estaría dando una instrucción a nadie: sería una
intención registrada que ningún sistema ejecuta. **Este proyecto ya se quemó con exactamente eso**
el 14 de septiembre, cuando el método de pago elegido por el comprador resultó ser una intención que
la pasarela no obedecía (`docs/09`).

## Decisión

**La modalidad se registra al conciliar, no se elige al despachar.**

`ConciliarRecaudo` ya le pedía a mano la comisión a quien concilia. Ahora le pide también **por cuál
de las dos vías entró el dinero** — créditos o banco—, que es un hecho que esa persona está viendo
en el panel de la plataforma cuando concilia. Registrar lo que se vio no necesita ninguna suposición:
es el mismo criterio con el que se resolvió la emisión indeterminada (`adr/0034`, decisión 5).

Con eso **la decisión 6 queda cerrada**, y la respuesta no es una de las dos opciones: es "las dos, y
cada envío dice cuál fue".

## La única regla que el dominio puede comprobar

**Los créditos no cobran comisión** (`docs/13` §3). Así que una conciliación que declare créditos con
una comisión encima está mal en una de las dos cosas —la modalidad o la cifra— y `Envio` la rechaza
antes de escribir nada. La misma invariante está en la base (`V52`), por lo mismo que el check de
positividad del paquete: hay caminos que escriben JPA directo y una invariante que solo vive en el
dominio no protege al que lo esquiva.

El formulario propone **créditos** por omisión, porque es la modalidad sin comisión: quien envíe sin
mirar deja registrado "no hubo comisión", que es lo que un formulario en blanco deja verdadero.

## Lo que esto NO desbloquea

**La conciliación automática del recaudo sigue sin construirse, y el motivo no cambió.** El
vocabulario de `on_delivery_status` no aparece declarado en ninguna fuente y ningún envío con recaudo
de esta cuenta llegó nunca a `success`, así que nadie ha visto un valor distinto de `null`
(`docs/13` §6.17). Mapear estados que no se han visto es la suposición que esta integración ya pagó
cuatro veces. El gancho que registra el vocabulario la primera vez que aparezca está puesto desde el
18 de septiembre y avisará solo.

Lo que sí cambia es que **cuando ese dato aparezca, la automatización ya no tropieza con la
comisión**: sabrá que a créditos vale cero, y el único caso que seguirá necesitando una persona es el
del banco.

## Tres hallazgos del camino, que valen más que el campo

**1. `@NotNull` no validaba nada — y aun así hacía algo.** La primera versión del DTO lo llevaba.
Este proyecto **no tiene proveedor de Bean Validation en el classpath** —lo dice
`OptionalValidatorFactoryBean` al arrancar— y no había ningún otro `@NotNull` en toda la capa de
presentación, aunque `apps/api/CLAUDE.md` afirmara que ahí se usa Bean Validation. Como validación,
otro guardián que nunca dispara, igual que el plugin de capas.

**Esta primera lectura era correcta y estaba incompleta**, y así estuvo escrita aquí unas horas: ver
el hallazgo 3.

**2. Jackson tampoco protegía, y la nota que decía que sí estaba medida sobre otro caso.**
`apps/api/CLAUDE.md` dice desde la Fase 6 que "Jackson 3 no rellena los componentes que falten de un
`record`" y que por eso revienta la deserialización. Es cierto **para un primitivo**: se midió sobre
un `boolean`. Un componente de **tipo referencia** —este enum— llega en nulo tan tranquilo. Se
comprobó mandando el cuerpo sin la clave: pasó de largo y murió más adelante por otra razón. Sin una
guarda explícita, ese nulo habría llegado al dominio y salido como un 500.

**3. Quitar el `@NotNull` cambió el contrato publicado, que era lo único que la anotación sí hacía.**
springdoc deduce de ella qué propiedades marca como `required` en el OpenAPI. Al quitarla, el
contrato pasó a declarar `modalidadRecaudo` como opcional y el cliente TypeScript generado dejó de
exigirlo en tiempo de compilación — mientras el servidor seguía rechazando con 422 el cuerpo que lo
omitiera: contrato y servidor diciendo cosas distintas.

Lo atrapó **el trabajo de contratos de la integración continua**, que compara el OpenAPI vivo contra
`packages/contratos/src/tipos.ts`, y es la primera vez que ese guardián dispara sobre algo real. La
corrección no es devolver el `@NotNull`: es un `@Schema(requiredMode = REQUIRED)`, que tampoco valida
y solo hace que el contrato diga lo que el servidor exige.

O sea que un campo obligatorio de tipo referencia necesita **dos anotaciones con oficios distintos**:
la guarda que protege y la que documenta. Y que "no valida" no es lo mismo que "no hace nada" — la
lección de los hallazgos 1 y 2 se aplicó a sí misma una vuelta más tarde.

La guarda es un `Objects.requireNonNull` en el constructor compacto del DTO, y sale como 422
`HTTP_MESSAGE_NOT_READABLE` porque Jackson envuelve la excepción. Con su prueba, y la prueba afirma
sobre el **código** y no solo sobre el estado: sin eso pasaba igual por la transición inválida del
pedido, que es una prueba que se aprueba a sí misma.

Los tres comparten forma con lo que este proyecto lleva encontrando toda la Fase 7: **una conclusión
correcta apoyada en una premisa que nadie había vuelto a medir.** El tercero es el más incómodo,
porque la premisa sin medir la puso este mismo documento unas horas antes.
