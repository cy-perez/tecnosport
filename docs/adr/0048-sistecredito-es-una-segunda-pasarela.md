# ADR-0048 — Sistecrédito es una segunda pasarela, no un medio más de Wompi

Fecha: 2026-09-20
Estado: aceptado
Relacionados: `adr/0029`, `docs/11-pagos-y-envios.md`, `docs/03-api.md`,
`docs/08-seguridad-legal.md`, `docs/14-consultas-al-abogado.md`

## Contexto

Sistecrédito entregó cinco guías (`G-ALI-08`, `G-ALI-09`, `G-ALI-10`, `G-ALI-12`,
`G-SCL-21`, todas de 2023) y tres credenciales. Leídas, describen una pasarela
propia —`https://api.credinet.co/pay`— que **no se parece a Wompi en nada que
importe**:

| | Wompi | Sistecrédito |
|---|---|---|
| Quién arma la URL de pago | el frontend, con la firma del backend | el backend, sondeando hasta que la pasarela la entregue |
| Autenticación | llave pública en la URL | tres cabeceras (`Ocp-Apim-Subscription-Key`, `ApplicationKey`, `ApplicationToken`) |
| Integridad al crear | firma `SHA256` sobre referencia, monto y moneda | no existe |
| Verificación del webhook | checksum firmado | **no existe**: se contrasta con una consulta |
| Ambiente de pruebas | llaves sandbox propias | no hay; solo un `sandbox` en el cuerpo |
| Devolución | desde el panel, a mano | anulación del crédito y el pagaré, a mano |

El código tiene hoy **un** puerto, `PasarelaDePagos`, con tres operaciones:
`generarFirmaIntegridad`, `verificarFirmaEvento` y `consultarTransaccion`. Las
dos primeras no tienen contraparte en Sistecrédito. La tercera se llama igual y
significa otra cosa.

Y hay un defecto que esto destapa, no que esto crea:
`MetodoPago.seProcesaPorPasarela()` dice "pasarela" y significa "Wompi".
`CrearIntentoDePago` enruta a Wompi todo lo que ese predicado apruebe. Con una
sola pasarela la mentira no costaba nada; con dos, el primer pedido de
Sistecrédito se iría a Wompi. Es exactamente el mismo modelo que miente y que
`docs/11-pagos-y-envios.md` ya dejó anotado con `ADDI`.

Dos hechos más, que llegaron de la asesora de Sistecrédito el 20 de septiembre de
2026 y que cambian el diseño:

- **Las credenciales son productivas y no hay ambiente de pruebas.** Cada
  transacción real es un crédito real a nombre de una persona real.
- **La anulación no tiene API.** Se hace en el portal Credinet, y la página
  pública de Sistecrédito para el consumidor la describe como una *reclamación*
  que **el comercio aliado** debe solicitar —"la anulación del crédito y el
  pagaré"—, no como un botón de devolución.

## Decisión

**1. Un puerto por pasarela.** Nace `PasarelaSistecredito` en `application`, con
las operaciones que esta pasarela sí tiene: crear la transacción y consultarla.
`PasarelaDePagos` se queda como está, con Wompi. No se generaliza a un puerto
común porque **no comparten una sola operación con la misma semántica**, y un
puerto que solo se cumple a medias en cada implementación es peor que dos
puertos honestos.

**2. `MetodoPago` deja de responder un booleano y responde quién lo resuelve.**
`seProcesaPorPasarela()` se convierte en `pasarela()`, que devuelve un valor de
`ProveedorDePago` (`NINGUNO`, `WOMPI`, `SISTECREDITO`). Sin `default` en el
`switch`, igual que hoy: un método nuevo no compila hasta que alguien decida de
qué lado cae.

> **Trampa que la implementación no puede pisar.** `MetodosDePagoDisponibles`
> filtra así: *si a ese método lo cobra alguna pasarela y no está en la lista de
> habilitados, quítalo*. Si `ADDI` se reclasificara a `NINGUNO` —que suena
> honesto, porque hoy ninguna pasarela lo procesa— **dejaría de filtrarse y se
> ofrecería siempre**. `ADDI` sigue apuntando a `WOMPI` y su `TODO` de `docs/11`
> sigue abierto; hay una prueba en cada lado de esa frontera.
>
> Al caso de uso llega **la unión** de lo habilitado por los dos proveedores, no
> un mapa por proveedor: la comprobación de que cada método le corresponde a
> quien lo habilitó vive donde se lee la configuración de ese proveedor
> (`PropiedadesMetodosDeWompi` rechaza `SISTECREDITO`), que es donde se puede dar
> un mensaje de error útil.

**3. La notificación no se cree; se contrasta.** La pasarela no firma sus
notificaciones. La propia guía lo dice y propone el remedio: con el `_id`
recibido se consulta `GetTransactionResponse` y se comparan `_id`, `invoice` y
`transactionStatus`. **Ese contraste es obligatorio antes de aplicar cualquier
cambio de estado**, no una comprobación de más. Es la regla dura #7 —el servidor
no confía en el cliente— aplicada a un cliente que además no se puede
autenticar.

**4. El `sandbox` del cuerpo es un freno de seguridad, y se trata como tal.**
Como no hay ambiente de pruebas, `sandbox.isActive` es lo único que separa una
prueba de un crédito real. Por eso: sale de configuración
(`SISTECREDITO_SANDBOX_ACTIVO`), **el arranque falla si viene encendido junto con
el perfil de producción**, y cada transacción creada en modo sandbox se registra
diciéndolo. Un booleano que puede costar un crédito a nombre de una persona no
se deja sin barandas.

**5. El documento de quien pide el crédito no se guarda.** Sistecrédito lo exige
para encontrar al cliente, así que hay que pedirlo y hay que enviarlo — pero no
hay motivo para conservarlo: viaja del checkout al caso de uso, de ahí a la
pasarela, y ahí termina. No hay columna, no hay entidad JPA y el `Pedido` no lo
conoce. Un reintento vuelve a pedirlo.

> Esto cambió sobre la marcha. El plan original le agregaba el documento al
> `Pedido`, con su migración y su campo en la API. Al escribirlo quedó claro que
> guardarlo compra una obligación —retención, borrado, respuesta a los derechos
> del titular— a cambio de ahorrarle al comprador teclear diez dígitos en el caso
> raro de un reintento. La minimización sale más barata para los dos.
>
> Lo que **no** desaparece es el deber de informar: el dato igual se transmite a
> un tercero, y eso la política de datos tiene que decirlo.

**6. El intento de pago se confirma antes de hablar con la pasarela, en su propia
transacción.** En la mitad de este caso de uso hay un tercero que abre una
solicitud de crédito a nombre de una persona, y eso ninguna transacción de base
de datos revierte: es exactamente el caso de `EnTransaccionPropia` (`ADR-0033`).

> Esto lo levantó la revisión adversarial y conviene que quede el porqué. La
> primera versión guardaba el `Pago` y después lanzaba la excepción del rechazo,
> con un comentario que afirmaba "el id se guarda SIEMPRE". Era falso: el
> controlador envolvía todo en un `TransactionTemplate`, que revierte ante
> cualquier `RuntimeException`.
>
> Lo caro no era perder la fila. El número de intento sale de contar los pagos
> del pedido, así que revertido el pago el contador se quedaba en cero y **cada
> reintento repetía la misma factura** — la que Sistecrédito ya tiene activa y
> rechaza con su `738`. El pedido quedaba imposible de pagar para siempre, con
> inventario reservado y un comprador que no entiende nada. Y los dos rechazos
> más probables, `801` y `802`, entran justo por esa rama.
>
> El segundo motivo para partirlo es el sondeo: con una transacción abierta,
> cada comprador retenía una conexión del pool hasta dos minutos.

**7. La anulación se registra, no se ejecuta** — y eso no es una concesión, es lo
que el sistema ya hace. `RegistrarReintegro` no mueve un peso a propósito: deja
la constancia de cuándo salió el dinero, por dónde y cuánto. Sistecrédito entra
por la misma puerta, con un valor nuevo en `MedioReintegro`.

**8. Un crédito no es un pago, y el modelo tiene que notarlo.** Cuando un pedido
pagado con Sistecrédito se retracta, lo que hay que deshacer **no es una
transferencia de dinero hacia el comprador**: es un crédito y un pagaré a su
nombre. Si nadie los anula, esa persona sigue pagando cuotas de algo que
devolvió. El `Reintegro` con medio `SISTECREDITO` significa "se solicitó y se
obtuvo la anulación del crédito", y el texto de la constancia tiene que decir eso
y no "se devolvió el dinero".

## Alternativas rechazadas

- **Generalizar `PasarelaDePagos` a un puerto común.** Quedaría un puerto con
  cinco operaciones de las que cada implementación cumple tres y lanza
  `UnsupportedOperation` en dos. El día que entre una tercera pasarela, peor.
- **Meter Sistecrédito como un `MetodoPago` más y resolverlo con un `if` dentro
  de `CrearIntentoDePago`.** Ese caso de uso quedaría conociendo a los dos
  proveedores, y la dirección de las dependencias aguanta pero la cohesión no: es
  el sitio donde el próximo proveedor se pega con cinta.
- **Esperar a tener ambiente de pruebas.** No existe para esta cuenta. Esperar
  algo que no va a llegar no es prudencia.
- **Confiar en la notificación y consultar solo de vez en cuando.** Una
  notificación sin firma a un endpoint público es un formulario que cualquiera
  puede rellenar. Sin el contraste, aprobar un pedido cuesta una petición HTTP
  desde cualquier parte del mundo.

## Consecuencias

**A favor:**

- El checkout gana un medio de pago que no exige tarjeta ni cuenta bancaria, que
  es justo el público que más pesa en Medellín para tecnología y calzado.
- El contraste obligatorio contra la consulta deja esta integración **más
  difícil de falsificar que la de Wompi**, no menos, pese a no tener firma.
- La forma del `Reintegro` ya existía; no hay que inventar un flujo de
  devoluciones.

**En contra, y hay que decirlo:**

- **Se prueba en producción.** No hay otra. El modo sandbox cubre casi todo,
  pero el recorrido del comprador —cuotas, token por SMS— exige al menos un
  crédito real.
- **Las llaves productivas viven en la máquina de desarrollo** mientras dure la
  integración. `docs/11` decía "ambiente de pruebas hasta que los recorridos
  completos pasen"; con este proveedor esa frase deja de ser cierta y hay que
  corregirla, no dejarla mintiendo.
- **El endpoint de confirmación no puede probarse en local.** Sin URL pública no
  llegan notificaciones, así que las pruebas van contra el despliegue de dev.
- **La devolución depende de una persona entrando a un portal.** Con un plazo
  legal corriendo por detrás.

## Lo que la revisión adversarial cambió

Los dos revisores del repositorio (`revisor-pagos` y `revisor-arquitectura`)
corrieron sobre la rama terminada y encontraron ocho fallos reales. Los cuatro
que importan, porque ninguno se notaba:

1. El intento que moría con la transacción que lo rechazaba (arriba, decisión 6).
2. **Todo** comprador aterrizaba en "no encontramos este pedido" después de
   pagar: la pantalla de estado exige `pedidoId` y `correo`, y Sistecrédito solo
   devuelve lo suyo. Ahora viajan en la URL de respuesta **como segmentos de
   ruta**, no como parámetros: las guías no dicen si la pasarela concatena con
   `?` o con `&`, y como parámetros una concatenación con `?` habría partido la
   URL en dos mitades ilegibles.
3. El endpoint que abre solicitudes de crédito era público y sin techo por IP.
   Con credenciales productivas, cualquiera podía disparar N solicitudes contra
   la cédula de cualquier persona, con su token por SMS. El `Idempotency-Key` no
   protege de eso: lo elige el cliente.
4. El freno del sandbox estaba escrito como lista negra sobre un texto libre
   —negar solo si el ambiente era exactamente `produccion`—, así que un typo, otra
   grafía o una variable sin fijar lo dejaban pasar. Ahora es lista blanca.

**El monto aprobado tampoco se verificaba.** De ida lo pone el servidor (regla
dura #7); de vuelta no había nada, y el contraste que propone la guía compara
`_id`, `invoice` y `transactionStatus` pero no el valor. Un crédito aprobado por
debajo de lo solicitado —un cupo tope, que es lo que hace un prestamista— se
habría aplicado como pago completo. Ahora se compara, y si no cuadra no se
aplica: `MONTO_NO_COINCIDE`.

## Lo que esto NO arregla

- **El monto mínimo del crédito sigue sin dato.** La búsqueda pública del 20 de
  septiembre de 2026 encontró dos cifras distintas publicadas por dos comercios
  aliados —$20.000 y $30.000— lo que confirma que **varía por comercio** y que
  ninguna de las dos sirve como dato nuestro. Queda como
  `TODO (dato de negocio): SISTECREDITO_MONTO_MINIMO`, a confirmar con la
  asesora. Mientras falte, el método no se ofrece por debajo de un piso que
  habrá que fijar, o se ofrece y la pasarela lo rechaza con un `802` que el
  comprador no entiende.
- **Si la anulación en Credinet notifica o no a `urlConfirmation`.** No es
  averiguable por fuera; hay que medirlo. Si no notifica, un pedido puede quedar
  marcado como pagado mientras la venta está anulada del otro lado y nada avisa.
- **Qué pasa con las cuotas que el comprador ya le pagó a Sistecrédito** antes de
  retractarse. La ley obliga a devolver "todas las sumas pagadas sin deducción
  alguna", pero esas sumas las recibió Sistecrédito, no el negocio. Va a
  `docs/14-consultas-al-abogado.md`; no se resuelve inventando una respuesta.
- **La comisión.** No es pública: la página de aliados dice que Sistecrédito
  "compra tu factura y te paga según la negociación pactada". Está en el contrato
  del negocio, no en internet.
