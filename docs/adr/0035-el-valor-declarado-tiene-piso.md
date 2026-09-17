# ADR-0035 — El valor declarado tiene piso

Fecha: 2026-09-17
Estado: aceptado
Relacionados: `adr/0021`, `adr/0033`, `docs/13-skydropx-capacidades.md` §6.4

## Contexto

Cada bulto de una cotización viaja con su `declared_amount`: el monto que la transportadora paga si
pierde el paquete, y sobre el que cobra el seguro. De dónde sale lo decidió `adr/0033` — del precio
congelado de la línea del pedido, porque es contra la factura que se reclama, y del precio del
catálogo cuando todavía no hay pedido, que es el caso del checkout.

**Skydropx valida un mínimo de 10.000 por bulto.** Un bulto declarado en 8.000 responde
`422 "El valor declarado debe ser mayor o igual a 10000"` y **tumba la cotización entera**, no ese
bulto. Está medido contra la cuenta, no deducido: `docs/13-skydropx-capacidades.md` §6.4.

El código no lo contemplaba, y el camino del fallo es este:

1. `ArmadorDeBultos` declara cada bulto con el precio de la variante, sin piso.
2. El `422` sale del cliente como excepción y `SkydropxClient.cotizar` atrapa `RuntimeException`
   entera —a propósito: cotizar no puede tumbar el checkout— y devuelve `NoSePudoCotizar`.
3. `CotizarEnvio` lo traduce a `CotizacionNoDisponibleException`, que es "no se pudo cotizar,
   intenta más tarde".

El comprador reintenta y no funciona nunca, porque **Skydropx deduplica las cotizaciones por
contenido** y la repetida ni siquiera se revalida: el mismo carrito contra el mismo destino
devuelve la misma respuesta congelada. Y nadie se entera del otro lado: el registro dice "proveedor
no disponible", que es mentira — el proveedor respondió, y respondió que nuestro cuerpo estaba mal.

Lo dispara cualquier carrito que lleve un artículo por debajo de 10.000: un cable, un cargador.
Hoy no se ha visto en la calle porque el catálogo de producción no está cargado y el sembrado es
ficción declarada como tal. Con el catálogo real es cuestión de tiempo, y el síntoma sería el peor
de todos — ventas que no ocurren, sin un error que las explique.

## Decisión

**El valor declarado de un bulto nunca baja del mínimo asegurable.** Cuando el precio queda por
debajo, se eleva; cuando está por encima o igual, se respeta tal cual.

### Se eleva en `ArmadorDeBultos`, no en el mapeador

El mapeador de la cotización es el único sitio que escribe `declared_amount`, así que elevar ahí
habría sido una línea. Se descartó: dejaría a `Bulto.valorDeclarado` diciendo 8.000 mientras lo que
se declara de verdad son 10.000, y un objeto que miente hacia adentro es exactamente lo que esta
integración lleva cuatro veces pagando caro — un campo en el sitio equivocado, sin error, y el
síntoma tres pasos después. Elevado en el armador, el bulto que circula por `application` dice lo
que se va a declarar, y el día que el panel o un correo muestren el asegurado sale de ahí.

Además cubre los dos caminos por construcción: la emisión recotiza pasando por el mismo armador, y
el envío hereda el declarado de la cotización (`MapeadorEmisionSkydropxV2`, §6.4). Una regla, un
sitio.

### El número entra por configuración, con el prefijo del proveedor

`tecnosport.skydropx.valor-declarado-minimo`, `SKYDROPX_VALOR_DECLARADO_MINIMO`, 10.000 por
omisión. Va con el prefijo de Skydropx porque **el mínimo es suyo** —lo dice su `422`— y se iría con
él si cambiáramos de plataforma. `bootstrap` se lo entrega al armador como un `Dinero` pelado, así
que `application` aplica la regla sin saber quién la exige.

## Alternativas rechazadas

- **Agrupar los bultos baratos en uno solo.** Obliga a inventar las dimensiones de la caja
  combinada, que es justo lo que la decisión del 11 de septiembre —un bulto por unidad— evitó
  porque las transportadoras cobran peso volumétrico. Un flete mal cotizado por unas medidas
  inventadas cuesta más que dos mil pesos de declarado.
- **Ofrecer solo recogida cuando haya un bulto por debajo del mínimo.** Castiga al comprador de un
  pedido de 400.000 porque lleva un cable de 8.000. La regla de negocio no dice eso en ninguna
  parte.
- **Dejarlo fallar**, que es el estado de hoy. Es la única alternativa que ya se probó, y lo que se
  sabe de ella es que falla en silencio.

## Consecuencias

- **Se declara más que la factura cuando hay varias unidades baratas.** El bulto es por unidad: tres
  cables de 8.000 son tres bultos elevados a 10.000, o sea 30.000 declarados contra 24.000
  facturados. No nos da nada —una reclamación por pérdida se paga contra la factura, no contra el
  declarado— y es el precio de cumplir el mínimo. Queda escrito para que nadie lo descubra leyendo
  una guía.
- **El seguro se cobra sobre el declarado, así que elevar cuesta plata.** ~~Cuánto, no se sabe: el
  proveedor no publica el porcentaje y este ADR no se lo inventa.~~ **Acotado el mismo día**, ver el
  final de este documento: dos de las tres tarifas vivas no se mueven con el declarado y la tercera
  cobra un 0,84 %. Sigue siendo observable sin adivinarlo, porque el flete real llega en `total` y
  `adr/0033` ya guarda por separado lo cobrado al comprador y lo que costó la guía.
- **La cotización y la emisión declaran lo mismo**, y no porque alguien las mantenga sincronizadas.
- **El declarado no se persiste**: se vuelve a calcular al recotizar en la emisión. Si algún día hay
  que probar qué se declaró en un envío viejo, hoy no se puede.

## Lo que esto NO arregla

**El `422` sigue disfrazado de caída del proveedor.** El piso quita la única causa conocida, no la
clase de fallo: cualquier otro `422` va a volver a contarse como `PROVEEDOR_NO_DISPONIBLE` y a
decirle al comprador que reintente algo que no va a funcionar. Que `SkydropxClient` atrape
`RuntimeException` entera es correcto y no se toca —cotizar no puede tumbar el checkout—, pero
tratar igual "el proveedor no responde" y "el proveedor rechazó nuestro cuerpo" hace invisible el
segundo, que es el único de los dos que se arregla del lado nuestro. Queda abierto y se trata
aparte.

**Y el techo del rango no se toca aquí, a propósito.** El formulario del panel acota el valor
declarado **entre 10.000 y 5.000.000** (`docs/13` §6.5), así que el mismo defecto podría existir
arriba: una variante de más de cinco millones tumbaría la cotización igual. No se implementa por dos
razones, y la segunda pesa más que la primera:

1. ~~**La evidencia no es la misma.** El mínimo lo dice un `422` medido; el máximo lo dice un campo
   de un formulario web. Nadie ha comprobado que la API lo valide.~~ **Medido el mismo día, un rato
   después** (`docs/13` §6.13): la API lo valida, el número es exactamente 5.000.000, el mensaje es
   simétrico al del mínimo y —como el mínimo— es **por bulto**. Esta razón ya no sostiene nada.
2. **Recortar no es simétrico a elevar.** Elevar un bulto barato no le quita nada a nadie. Bajar un
   celular de 6.000.000 a 5.000.000 declararía por menos de lo que vale, y si se pierde, la
   transportadora responde por el tope y el millón restante lo pone el negocio. Eso es una decisión
   de negocio con plata encima, no un ajuste de borde. **Sigue en pie, y ahora es lo único que
   queda**: el dato ya está, falta la decisión.

**Lo que la medición del techo también respondió, y este ADR daba por desconocido:** el declarado
mueve el precio en **una** de las tres tarifas vivas. Servientrega y Envía cobran lo mismo con un
millón que con cinco; Coordinadora cobra un 0,84 % más, así que elevar un bulto de 8.000 a 10.000
cuesta del orden de diecisiete pesos con ella y cero con las otras dos.
