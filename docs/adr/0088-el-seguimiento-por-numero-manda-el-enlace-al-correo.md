# ADR-0088 — El seguimiento por número manda el enlace al correo

**Fecha:** 2026-10-25
**Estado:** aceptado. Reemplaza `ConsultarSeguimientoPorNumero`.

## Contexto

El formulario de "Estado del pedido" del pie existe porque el comprador no
conoce el `id` de su pedido, que es un UUID. Lo único que tiene en la mano es el
número del comprobante, `TS-2026-000123`. `POST /api/v1/pedidos/seguimiento`
recibía ese número y el correo, y si coincidían **devolvía el pedido**: qué
compró, a dónde se lo llevan, en qué estado está y con qué guía.

El número es secuencial. Lo único que protegía el pedido era el correo, y
conocer el correo de alguien no es difícil. El código lo decía en voz alta: quien
conoce el correo de una persona puede recorrer números hasta dar con uno suyo.
El límite de diez consultas cada diez minutos por IP lo encarecía, no lo
cerraba, y además se podía evadir cambiando una cabecera (ADR-0087).

## Decisión

1. **El formulario ya no muestra el pedido.** Si el número y el correo
   coinciden, el servidor le manda a ese correo el enlace a la pantalla de
   estado. Es el mismo enlace del comprobante y del correo de despacho: entra por
   el `id` y lleva el correo en el fragmento.
2. **La respuesta es 202 sin cuerpo en todos los casos**: acierto, correo
   ajeno, número inexistente, número mal escrito, correo con formato imposible y
   tope alcanzado. La pantalla dice lo mismo acierte o no.
3. **Tres correos por hora para cada pedido**, decidido por el negocio el 25 de
   octubre de 2026. El límite por IP sigue delante y frena a quien recorre
   números; este protege el buzón del titular.
4. El enlace se arma en un solo sitio, `EnlaceDeEstado`. Antes estaba copiado en
   el comprobante y en el despacho, y esta iba a ser la tercera copia.

Con esto el ataque desaparece en vez de encarecerse. Quien acierta el número de
alguien cuyo correo conoce no ve nada: lo único que consigue es que a esa
persona le llegue su propio enlace.

## Alternativas descartadas

- **Un código de seguimiento aleatorio en el comprobante**, pedido junto al
  número. Exige una migración y rellenar los pedidos que ya existen, y obliga a
  la gente a teclear un código largo que solo tiene en el correo. Si ya lo tiene
  en el correo, el enlace del correo hace lo mismo sin que tenga que teclear
  nada.
- **Subir el límite por IP.** Seguiría encareciendo el ataque en vez de
  cerrarlo, y le estorbaría a quien consulta de buena fe.

## Consecuencias

- Quien perdió el correo y no recuerda cuál usó no puede ver su pedido por el
  sitio. Antes tampoco podía: el formulario ya pedía ese mismo correo.
- **El acierto hace más trabajo que el fallo**: carga el agregado, escribe en el
  limitador y en la bandeja de salida. Contra la base son decenas de
  milisegundos, constantes y promediables, y dirían si un correo es de un
  cliente y, por el número, más o menos cuándo compró: dato personal. Por eso el
  controlador **responde siempre después de un piso de 400 ms**, y un fallo al
  encolar se registra y responde 202 como todo lo demás: un 500 solo podría
  darse en el camino que acierta. Lo levantó la revisión de seguridad; el primer
  borrador de este ADR lo daba por inofensivo.
- **El endpoint no abre transacción.** El limitador abre la suya, y con otra
  abierta en el controlador cada petición retenía una conexión mientras pedía
  una segunda: diez a la vez agotaban el pool. La bandeja de salida confirma su
  fila sola, como en las tareas.
- Quien conozca el número y el correo de alguien puede gastarle los tres
  correos de la hora. La pantalla le dirá a esa persona lo mismo de siempre, y
  el enlace le llegará en la hora siguiente. Se acepta: hacerlo exige conocer los
  dos datos, y no expone nada del pedido.
- `POST /pedidos/{id}/seguimiento` no cambia. El id es un UUID v7: recorrerlo no
  es una opción, y es el único camino por el que se ve un pedido sin sesión.
