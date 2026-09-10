# ADR 0027. Los agregados no llevan versión, y el escenario que eso deja abierto

Fecha: 2026-09-10. Estado: aceptada.

## Contexto

Una revisión adversarial del bloque del retracto y de la atención buscó bloqueo
optimista en los agregados nuevos —`SolicitudRetracto`, `ReclamacionGarantia`,
`SolicitudReversion`, `SolicitudAtencion`, `Reintegro`— y no lo encontró. Al
mirar si era una omisión de ese bloque apareció el dato que cambia la pregunta:
**ninguna de las veinticinco entidades JPA del proyecto tiene `@Version`.** No es
una omisión de una fase, es una decisión que nunca se tomó explícitamente.

El escenario que eso deja abierto, dicho sin adornos: dos administradores
operando la misma solicitud a la vez. Cada uno la lee en su propia transacción,
los dos pasan la guarda de la máquina de estados —porque los dos leyeron el mismo
estado— y el segundo `commit` sobrescribe al primero sin ruido. Uno registra el
reintegro y el otro rechaza la solicitud: gana el que llega tarde, y el historial
no dice que hubo una carrera.

Lo que ya está protegido, y conviene no confundirlo con lo que no:

- **El dinero.** `ux_reintegro_origen` (`V23`) es un índice único sobre
  `origen_id`, así que dos constancias de reintegro para la misma solicitud, la
  misma reclamación o la misma reversión no se pueden escribir, corran o no en
  paralelo. Y desde `TopeDeReintegro`, la suma de lo devuelto por un pedido no
  puede pasar de su total.
- **El inventario.** `RepositorioInventarioJpa` usa bloqueo pesimista desde la
  Fase 2, con una prueba de concurrencia real de dos hilos por la última unidad.
- **El consecutivo del pedido.** `V6` lo resuelve con un `insert ... on conflict
  ... returning`, con prueba de veinte hilos.

O sea: los tres sitios donde una carrera cuesta plata o vende dos veces la misma
unidad tienen su guarda, y la tienen probada. Lo que queda sin proteger es el
**estado de una solicitud de atención**, que es una carrera entre dos personas de
la misma oficina.

## Decisión

**No se agrega `@Version` a los agregados, y la razón queda escrita en vez de
quedar implícita.**

Tres cosas la sostienen:

1. **Hoy hay un solo `ADMIN`.** `SembradorAdmin` crea uno y nunca lo actualiza;
   no hay pantalla para crear más, y la rotación de la clave de un `ADMIN` ya
   creado sigue sin construirse. La carrera necesita dos operadores, y no hay dos.
2. **`docs/03-api.md` ya razonó sobre esto** al decidir que las acciones
   administrativas no llevan `Idempotency-Key`, porque la máquina de estados de
   `Pedido` las hace idempotentes. Es el mismo supuesto de un solo actor, tomado
   antes y para el mismo tipo de operación; versionar solo los cinco agregados
   nuevos habría dejado el proyecto con dos criterios distintos para el mismo
   problema.
3. **El costo real no es la columna.** `guardar` reconstruye la entidad JPA desde
   el agregado en cada llamada, así que una entidad nueva llega siempre con
   versión nula y Hibernate no tendría nada que comparar. Para que `@Version`
   sirviera, el número tendría que viajar dentro del agregado de dominio — o sea,
   meter una preocupación de persistencia en `domain`, que es justo lo que la
   regla dura #1 mantiene fuera. Es un cambio de diseño, no una anotación.

## Consecuencias

- Dos administradores simultáneos sobre la misma solicitud pueden perder una de
  las dos escrituras, sin error y sin rastro. El dinero no se duplica, el
  inventario no se descuadra: lo que se pierde es un cambio de estado o una nota.
- **El disparador para volver aquí es concreto: el día que exista un segundo
  operador.** No "cuando crezca el tráfico" —el tráfico de compradores no toca
  esto— sino cuando haya dos personas con rol `ADMIN`. Ese día esta decisión
  caduca, y la salida preferida no es `@Version` sino la de menor daño al
  dominio: que el `guardar` de los repositorios afectados haga un `update ...
  where estado = <el que se leyó>` y trate cero filas afectadas como conflicto.
  Protege lo mismo, no obliga al agregado a saber de versiones, y se puede aplicar
  agregado por agregado.
- Queda dicho, además, lo que **no** cambia con esto: las guardas del dinero, del
  inventario y del consecutivo son independientes de esta decisión y no se
  relajan.

## Alternativas consideradas

- **`@Version` en los cinco agregados nuevos.** Rechazada por lo de arriba: exige
  el número en el dominio y deja veinte entidades sin versionar, con dos criterios
  conviviendo.
- **Actualización condicional por estado, ahora.** Es la salida correcta el día
  que haga falta, y se rechaza hoy solo por orden: cambia el contrato de los
  repositorios y obliga a cada caso de uso a recordar el estado que leyó, y eso es
  trabajo con una prueba de concurrencia por agregado para un escenario que hoy no
  existe. Queda escrita como la vía preferida para no volver a discutirlo.
- **Dejarlo sin decidir.** Es lo que había, y es lo peor de los tres: el riesgo
  seguía existiendo igual, pero nadie podía verlo sin auditar veinticinco
  entidades.
