# ADR-0072 — La recogida en el punto se apaga con una bandera del servidor

**Fecha:** 2026-10-08
**Estado:** aceptado. Cambia la salida que daban `ADR-0036`, `ADR-0046` y
`ADR-0071` a los carritos que no pueden ir a domicilio.

## Contexto

Hasta hoy la recogida en el punto de Medellín no tenía interruptor: el checkout
la ofrecía siempre, y era la salida de todo carrito que no podía ir a domicilio
—un artículo que no se puede asegurar (`ADR-0036`), tecnología sin medidas
(`ADR-0046`, `ADR-0071`), un destino sin cobertura o una cotización rechazada—.
Los términos (§4 y §8), las preguntas frecuentes, la página de contacto y el
carrito la prometían, gratis.

El 8 de octubre de 2026 el negocio decidió dejar de ofrecerla. Y decidió también
qué pasa con esos carritos: **se bloquean y se remiten a WhatsApp**, en vez de
dejar la recogida como respaldo escondido o bloquearlos sin salida.

## Decisión

1. **Bandera de servidor, apagada por omisión.** `RETIRO_EN_PUNTO_HABILITADO`
   (`tecnosport.retiro-en-punto.habilitado`) alimenta `ModalidadesDeEntrega`.
   `CrearPedido` rechaza un `RETIRO_EN_PUNTO` con 409
   `RETIRO_EN_PUNTO_NO_DISPONIBLE` **antes de reservar nada**. La regla 7 manda:
   esconder la opción en la pantalla no basta.
2. **El checkout pregunta, no supone.** `GET /api/v1/envios/modalidades` dice qué
   se ofrece. Con una sola modalidad el selector de tipo de entrega desaparece.
   Mientras la respuesta no llega, o si falla, la pantalla actúa como si la
   recogida estuviera apagada: es el lado que el servidor no va a rechazar.
3. **La salida se escribe una vez.** Los mensajes de "no hay envío" dejaron de
   terminar en "recoge en nuestro punto". La salida la pinta
   `ts-salida-sin-envio`: la recogida si está encendida, WhatsApp si no.
4. **Los textos dejan de prometerla.** Términos versión `2026-10-08` (§4 y §8),
   preguntas frecuentes, contacto y carrito. Los textos son estáticos y no leen
   la bandera.

## Consecuencias

- **Encender la bandera no basta para volver a ofrecer la recogida.** Los
  términos dicen ahora "no hay recogida en nuestra dirección", así que la
  recogida y su texto vuelven juntos, con la skill de textos legales.
  `PropiedadesRetiroEnPunto` lo dice en su javadoc.
- **Los carritos que solo se podían recoger no se venden en el sitio.** Eso es
  la tecnología de más de 5.000.000, la que no tiene medidas y los destinos sin
  cobertura. Es venta que se pierde o se atiende por WhatsApp a mano, y es lo que
  el negocio eligió.
- La contraentrega se encendió el mismo día, **solo en dev**
  (`infra/envs/dev/main.tf`). Producción espera la confirmación de Skydropx sobre
  comisión, tope y plazo de giro del recaudo (`ADR-0023`).
- De paso se cerró un hueco: el resumen no tenía rama para
  `ARTICULO_SIN_MEDIDAS` y la confirmación no lo tenía en su mensaje de bloqueo.
  La fila del costo quedaba vacía, sin motivo.
