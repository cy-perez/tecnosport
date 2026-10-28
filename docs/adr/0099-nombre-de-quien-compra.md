# ADR-0099: el checkout pide el nombre de quien compra, aparte del de quien recibe

Fecha: 2026-10-27
Estado: aceptada.
Relacionados: `adr/0098` (la plantilla y el saludo de los correos), la migración `V36` (el
contacto de quien recibe), `docs/02-modelo-datos.md`.

## Contexto

El checkout pedía un solo nombre, el de **quien recibe**: va en la guía de la transportadora, es
por quien pregunta el mensajero y con el que se reclama un pedido en el punto. Desde el saludo de
los correos (`adr/0098`) era también el nombre con el que se le escribía a quien compró, porque no
había otro. Así, un regalo le llegaba al comprador diciendo «Hola, Luisa:», con Luisa como la
destinataria.

## Decisión

1. **Dos nombres.** El pedido guarda `nombre_comprador` (`V148`) aparte de `nombre_contacto`. Es
   nulo en los pedidos anteriores; `CrearPedido` lo exige en los nuevos, antes de reservar nada,
   como ya hacía con el contacto. Los dos pasan por `NombreDePersona`, la regla que vivía dentro de
   `Contacto` (letras, apóstrofo, guion, punto; hasta 120).
2. **El checkout no pide dos veces lo mismo.** «Tu nombre» es obligatorio; la casilla «Lo recibe
   otra persona», desmarcada por omisión, hace aparecer «Nombre de quien recibe». Desmarcada, se
   manda el mismo nombre en los dos sitios. «Teléfono de contacto» no cambia: es el número al que
   llama el mensajero.
3. **Los correos saludan a quien compra** (`Pedido.nombreParaSaludar()`); un pedido anterior a esto
   saluda, como antes, a quien recibe, y uno anterior al contacto, sin nombre.
4. **El comprobante dice «Comprado por:»**, y «Recibe:» si es otra persona: es el soporte para
   reclamar la garantía y hasta ahora no decía a nombre de quién quedó la compra.
5. **El panel** muestra «Quien compra» en el detalle cuando no es quien recibe.
6. **El seguimiento no lo expone.** Se abre con un enlace y lo que muestra es a quién se entrega.

La API rechaza un pedido nuevo sin `nombreComprador` (`422`). Se hizo obligatorio de una vez porque
solo existe el ambiente de desarrollo, sin compras reales que pudieran caer en el minuto en que la
API va por delante de la web.

## Lo que no cambió

- **La política de datos.** Ya declara el nombre entre los datos de identificación que se recogen;
  el de quien compra no es una categoría nueva.
- **La guía de Skydropx** sigue llevando a quien recibe.
- **Las cuentas** siguen sin nombre: el registro no lo pide, y sus correos saludan con «Hola:».
