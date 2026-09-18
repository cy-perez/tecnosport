# ADR-0037 — El recaudo se declara, porque el flete no se delega

Fecha: 2026-09-17
Estado: aceptado
Modifica: `adr/0023`. Matiza `adr/0035`.
Relacionados: `adr/0012`, `adr/0033`, `adr/0036`, `docs/13-skydropx-capacidades.md` §6.5 y §6.15

## Contexto

`ADR-0023` decidió que **lo que la transportadora cobra en la puerta es el total del pedido, flete
incluido**, y que sale de `Pedido.total()`. No quedó solo en el ADR: el javadoc de `Pedido.total()`
lo nombra entre las tres cosas que cuelgan de ese método, y el `application.yml` dice textualmente
que los límites de la contraentrega "se comparan contra el TOTAL del pedido, flete incluido: es lo
que se cobra en la puerta".

**El código no lo hace.** `ArmadorDeBultos` declara el precio de la unidad, elevado al mínimo
asegurable, y `MapeadorEmisionSkydropxV2` no manda ningún monto a recaudar. Nadie perdió plata
todavía porque `CONTRAENTREGA_HABILITADA` está en falso; encenderla tal como está habría dejado
**el flete sin recaudar en cada pedido contraentrega**, y lo habría puesto el negocio sin que
ninguna prueba fallara.

Lo que faltaba para decidir cómo arreglarlo se midió el 17 de septiembre (§6.15):

- **No hay campo para el monto a recaudar.** No es que esté mal escrito —se probaron diez grafías
  el 11 de septiembre—: no existe. El monto sale calculado en `on_delivery_amount`, y lo que la
  plataforma calcula es **el valor declarado**.
- **`recipient_pays_shipping` no suma el flete.** La cotización lo acepta y lo devuelve en `true`,
  y el monto sigue siendo el declarado pelado. Medido en tres puntos con 10.000 declarados
  —cotización sin la bandera, con la bandera, y el envío ya creado y **pagado**— y los tres
  responden 10.000 exactos. El "si aplica" que la documentación pone junto al flete no aplica en
  esta cuenta.

O sea: **el único número que controlamos es el valor declarado.** Si el recaudo tiene que valer
`Pedido.total()`, hay que declararlo.

## Decisión

**Lo que se recauda se construye declarando.** En un pedido contraentrega, la suma de los valores
declarados de sus bultos es exactamente `Pedido.total()`.

1. **El flete se reparte entre los bultos en la emisión, no en la cotización del checkout.** Ahí
   está la única forma de no morderse la cola: el `costoEnvio` que se reparte es el que el pedido
   **ya congeló**, no el que la cotización está calculando en ese momento. `EmitirGuiaDePedido`
   recotiza siempre (`adr/0033`), así que el sitio existe y no hay que inventarlo.
2. **El reparto es proporcional al valor de cada bulto**, con el residuo de la división en el de
   mayor valor. Se elige proporcional y no "todo en uno" para que el declarado de cada bulto siga
   pareciéndose a lo que lleva dentro, que es lo que `ADR-0035` pidió cuando puso el piso en
   `ArmadorDeBultos` y no en el mapeador.
3. **Los dos extremos del rango se validan después de sumar el flete**, no antes: el piso de
   `ADR-0035` y el techo de `ADR-0036`. El flete es parte de lo declarado, así que es parte de lo
   que la plataforma valida.
4. **Si la suma de los declarados con piso ya supera `Pedido.total()`, ese pedido no se ofrece
   contraentrega.** Pasa cuando la inflación del piso es mayor que el flete: un carrito de muchas
   unidades muy baratas. Diez cables de 8.000 son 100.000 declarados contra 80.000 de mercancía, y
   ningún flete de un envío nacional cierra esa diferencia de 20.000. **Cobrar en la puerta más de
   lo que el pedido dice no es una opción**: es una diferencia que el comprador ve en el momento de
   pagar, con el paquete en la mano y sin haber aceptado nada de eso.
5. **Solo aplica a la contraentrega.** Un pedido pagado en línea declara lo que vale la mercancía,
   exactamente como hoy: ahí el declarado no cobra nada, solo asegura.

## Alternativas rechazadas

- **Delegarlo en `recipient_pays_shipping`.** Es la que el nombre del campo promete y la que habría
  costado una línea. **Está medida y no hace nada** (§6.15). No se rechaza por preferencia.
- **Que el recaudo sea solo la mercancía y el flete se regale.** El flete dejó de estar incluido en
  el precio cuando el envío pasó a cotizarse por destino, y `ADR-0023` ya había decidido lo
  contrario. Sería regalar un flete en cada contraentrega, y el pedido que más flete cuesta —el más
  lejano— es justo el que más se paga contra entrega.
- **Poner todo el flete en un solo bulto.** Más simple y sin residuos de división, pero distorsiona
  un bulto entero y lo acerca al techo de `ADR-0036` por una razón que no tiene nada que ver con lo
  que ese bulto lleva dentro.
- **Recortar el declarado de algún bulto por debajo del piso para cuadrar la suma.** La plataforma
  responde `422` y tumba la cotización entera, no ese bulto (§6.4). Es lo que `ADR-0035` ya midió.
- **Guardar el monto a recaudar en nuestro lado y reconciliarlo contra el extracto.** Mueve el
  problema a la contabilidad sin arreglarlo: la transportadora seguiría cobrando el declarado, y la
  diferencia aparecería después, cuando ya no se puede pedir.

## Consecuencias

- **En contraentrega, el valor declarado deja de significar "lo que vale la mercancía" y pasa a
  significar "lo que se cobra en la puerta".** `ADR-0035` fijó lo primero; esto lo matiza según el
  método de pago, y por eso queda escrito donde se lea: es la clase de doble sentido que, sin
  decirlo, alguien deshace de buena fe seis meses después.
- **El seguro se cobra sobre el declarado, así que en contraentrega también se asegura el flete.**
  Con el 0,84 % de la única tarifa que se mueve con el declarado, un flete de 10.000 cuesta 84
  pesos de prima. Es observable y no hay que adivinarlo.
- **Una reclamación por pérdida de un contraentrega está declarada por encima de la factura de
  mercancía.** No da nada: se paga contra la factura. Igual que la inflación del piso de
  `ADR-0035`, es el precio de que el declarado tenga dos trabajos.
- **La contraentrega deja de ofrecerse en carritos de muchas unidades muy baratas.** Es raro y es
  a propósito. **El checkout no explica por qué**, y eso no es una omisión de esta decisión: no lo
  explica para ninguna de las razones por las que hoy se retira la contraentrega —la ciudad sin
  cobertura, el monto fuera de rango, la categoría excluida, un rechazo en entrega anterior—. La
  lista de métodos simplemente llega sin ella. Queda anotado como lo que es: una deuda vieja que
  este ADR hereda y no agrava. `TODO (producto): decidir si el checkout explica por qué no hay
  contraentrega, para las cinco razones a la vez o para ninguna.`
- **El mismo artículo puede ser asegurable pagando en línea y no pagando contraentrega**, si el
  flete repartido lo empuja sobre el techo. El checkout ya sabe decir cuál artículo es y con su
  nombre (`ADR-0036`), así que no hace falta un mensaje nuevo.

## Lo que esto NO arregla

**`on_delivery_status` sigue sin leerse.** `ADR-0023` dijo que el estado del recaudo llegaría por el
mismo webhook del seguimiento, y hoy `RECAUDO_CONCILIADO` lo pone una persona a mano. El campo
existe en el envío y está medido (§6.15), así que lo que falta es código, no información. Queda
abierto y se trata aparte, porque conciliar es otro caso de uso.

**Y esto no enciende la contraentrega.** Lo que la enciende es el trámite del servicio en la cuenta
de producción (§3), que es de negocio y no de código. Además, hoy en el sandbox **la única
transportadora que puede emitir con recaudo es 99 minutes**, un 65 % más cara que la más barata que
cotiza: Coordinadora tiene el contador de remisiones atascado y Envía responde `Usuario o Password
incorrecto at LABEL_NUMBER` (§6.15). Eso hay que remedirlo en producción antes de prometerle a
alguien un plazo o un precio de contraentrega.
