# ADR 0029. Los medios de pago que se ofrecen los decide la cuenta de la pasarela, no el código

Fecha: 2026-09-14. Estado: aceptada.

## Contexto

Addi estudia la activación de un comercio con el sitio ya en línea, así que no
puede estar el día del lanzamiento. Quitarlo del checkout destapó que **no había
por dónde quitarlo**: `MetodosDePagoDisponibles` partía de `EnumSet.allOf` y solo
retiraba contraentrega cuando no aplicaba. El checkout ofrecía todo lo que el
código sabía procesar, estuviera o no activado en la cuenta de Wompi, y
`CrearPedido` solo revalidaba contraentrega, así que un cliente que posteara
cualquier otro método lo creaba igual.

Ofrecer un método apagado no reventaba nada, y ese era el problema. La URL del
Web Checkout hospedado **no le manda a Wompi el método que el comprador eligió**:
Wompi pinta su propia lista y el comprador vuelve a elegir allí. Un comprador
elegía Addi, pagaba con tarjeta y el pedido quedaba grabado diciendo Addi. Es
decir: `pedido.metodo_pago` siempre fue una intención, y nada la contrastó nunca
contra lo que se cobró.

Al revisar la tabla de `docs/11-pagos-y-envios.md` apareció un tercer hecho: la
documentación pública de Wompi (consultada el 14 de septiembre de 2026, regla
dura #9) **no lista Addi entre sus medios**. Lo que Wompi tiene en esa familia es
`BANCOLOMBIA_BNPL` y `SU_PLUS`; Addi es un proveedor aparte con integración
propia. `MetodoPago.ADDI` marcado como método de pasarela describe algo que no
existe.

## Decisión

**Qué se ofrece lo dice la configuración, no el enum.**
`tecnosport.wompi.metodos.habilitados` (`WOMPI_METODOS_HABILITADOS`) es la lista
de lo que la *cuenta* de Wompi tiene activado, separada por comas. Por omisión
`TARJETA,PSE,NEQUI,BANCOLOMBIA`; Addi fuera. Solo admite métodos que pasen por la
pasarela: contraentrega y transferencia manual tienen su propio interruptor y un
segundo los haría divergir. Un nombre que no exista impide arrancar, porque un
despliegue con la lista mal escrita tiene que fallar al arrancar y no al primer
checkout. La lista vacía es legítima: significa que la pasarela no acepta nada
hoy y el checkout se queda con transferencia manual y contraentrega.

**Son dos preguntas y las dos las hace el servidor, dos veces.**
`MetodosDePagoDisponibles.habilitados()` responde "¿ofrece el negocio ese método
hoy?" sin mirar el pedido y sin llamar a nadie; `ejecutar()` parte de ahí y
responde "¿le sirve a este pedido?", que hoy solo condiciona a contraentrega.
`CrearPedido` exige la primera antes de nada y la segunda después, y responde
`409 METODO_DE_PAGO_NO_HABILITADO` o `409 CONTRAENTREGA_NO_DISPONIBLE`. Son dos
códigos porque son dos negativas distintas: "para ningún pedido" y "para este".
`409` y no `400`: la petición está bien formada y el método existe; lo que cambió
es qué se acepta, y pudo cambiar entre la consulta y el pedido.

**Qué métodos pasan por la pasarela lo dice el enum, en un solo sitio.**
`MetodoPago.seProcesaPorPasarela()` sustituye al `switch` privado de
`CrearIntentoDePago`, y no lleva `default`: un método nuevo no compila hasta que
alguien decida de qué lado cae.

**El método elegido y el medio cobrado son dos hechos, y se guardan los dos.**
`V39` agrega `pago.medio_reportado_pasarela`, el `payment_method_type` que Wompi
reporta por webhook o por conciliación; `PasarelaDePagos.consultarTransaccion`
devuelve ahora `TransaccionDePasarela` —estado y medio— y no solo el estado, que
dejaba a la conciliación cerrando pedidos sin enterarse de con qué se cobró. Se
guarda **crudo**, tal como Wompi lo nombra, para que un valor que hoy no se sepa
traducir no se pierda en el mapeo; se queda con lo último que la pasarela dijo, y
un evento que no lo trae no borra lo que ya se sabía. **No pisa `metodo_pago`**:
machacarlo borraría la única prueba de que el sitio ofreció una cosa y cobró
otra. `MediosDeWompi` traduce al enum los valores que este sitio ofrece —`CARD`,
`NEQUI`, `PSE`, `BANCOLOMBIA_TRANSFER` y `BANCOLOMBIA_QR`— y devuelve nulo para
el resto; "no sé traducir esto" no es "esto no coincide".

**`MetodoPago.ADDI` se queda, y no se ofrece.** Decidir qué hacer con él es un
dato de negocio (`TODO` en `docs/11`): integrar Addi directo cuando lo aprueben,
y entonces deja de ser método de pasarela, o quitarlo y ofrecer el BNPL de
Bancolombia, que sí entra por esta configuración.

## Alternativas

**Quitar `ADDI` del enum.** Una línea, y cierra el caso de hoy. Se descartó porque
deja intacto el agujero: el siguiente método que Wompi apague en la cuenta se
seguiría ofreciendo, y volver a poner Addi el día que lo activen sería una
migración y un despliegue en vez de una variable de entorno.

**Una lista fija en código.** Es lo que había, con otro nombre. Lo que la cuenta
tiene activado cambia sin que cambie el código, y el sitio ya demostró tres veces
que una regla que vive donde nadie la ejecuta se rompe sin avisar.

**Sobrescribir `metodo_pago` con lo que Wompi reportó.** Deja el pedido "correcto"
y borra la evidencia de la discrepancia, que es justo lo que hay que poder
demostrar si un comprador reclama que se le cobró con algo que no eligió.

**Traducir el medio antes de guardarlo.** Un valor nuevo de Wompi se perdería en
el `default` del mapeo. Crudo se guarda siempre; la traducción se hace al leer y
puede mejorar sin migración.

## Consecuencias

- `PropiedadesMetodosDeWompi` en `bootstrap`, activada por `ConfiguracionWompi` e
  inyectada en `ConfiguracionEnvio`, que es donde se arma
  `MetodosDePagoDisponibles`: el caso de uso vive en `application.envio` por la
  contraentrega, pero lo que la pasarela tenga activado no es asunto del envío.
- `MetodoDePagoNoHabilitadoException` en `application.pedido`, mapeada a `409`.
- Migración `V39`; `Pago` gana `medioReportadoPorLaPasarela()` y
  `registrarMedioReportadoPorLaPasarela`; `ProcesarEventoDePagoComando` lleva
  `medioWompi` y `LectorEventoWompi` lo lee del JSON crudo; `WompiClient` lo
  devuelve en la consulta directa.
- El numeral 6 de los términos dejó de nombrar a Addi y dice que los medios
  disponibles son los que se muestran al pagar. **El día que Wompi active Addi hay
  que devolver esa frase**, además de agregarlo a la variable de entorno.
- `WOMPI_METODOS_HABILITADOS` entra en la lista de `docs/07-infra-gcp.md`. No se
  declara en Terraform: el valor por omisión del `application.yml` ya excluye a
  Addi, y ese es el valor correcto hasta nuevo aviso.
- **Deuda declarada:** nadie compara todavía el método elegido con el medio
  reportado. `MediosDeWompi` está probado y no tiene llamadores en producción;
  ninguna pantalla muestra `medio_reportado_pasarela`. Comparar sin haber decidido
  qué hacer con la discrepancia sería una alerta sin dueño.
- `esMetodoPagoWompi` en `apps/web` sigue siendo un espejo escrito a mano de
  `seProcesaPorPasarela()`, con `ADDI` dentro. Es correcto mientras el servidor no
  lo ofrezca.
