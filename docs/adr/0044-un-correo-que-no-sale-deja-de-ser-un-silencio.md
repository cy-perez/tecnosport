# ADR-0044 — Un correo que no sale deja de ser un silencio

Fecha: 2026-09-18
Estado: aceptado
Relacionados: `adr/0028` (el vigilante del plazo), `adr/0034` (la bandeja de revisión),
`adr/0042` (el comprobante es el documento de la venta), `docs/08-seguridad-legal.md`

## Contexto

`EnviadorDeCorreoSpringMail`, el único adaptador de correo de producción, registraba el fallo de
SMTP y **se lo tragaba**. Lo levantó la revisión adversarial de los 122 commits (`docs/09`,
2026-09-18) y quedó anotado como lo único de esa lista que no se cerró ese día.

La razón por la que se tragaba era buena y estaba escrita: `SolicitarRecuperacion` responde 204
exista o no la cuenta, y solo llega a enviar cuando **sí** existe — si un SMTP caído subiera hasta
un 500, ese 500 aparecería exactamente en las cuentas reales y en ninguna otra. Es el oráculo de
enumeración que todo ese diseño evita.

**El sitio estaba equivocado, no la razón.** Tragarlo en el adaptador tomaba la decisión para los
**doce** llamadores, y la mayoría necesita lo contrario.

## Lo que el silencio costaba, caso por caso

- **El comprobante de compra.** `EnviarComprobantesDeCompra` reclama el comprobante antes de
  mandarlo —es lo que impide dos correos al mismo comprador— y tiene escrito desde la revisión
  adversarial un `catch` que devuelve el reclamo para reintentar. **Ese `catch` no se ejecutaba
  nunca.** Un SMTP caído dejaba la marca puesta con el correo sin salir, y como la consulta ya no
  trae ese pedido, ese comprador se quedaba sin comprobante **para siempre**. Desde `adr/0042` eso
  no es un correo de cortesía: es el documento de la venta, el que sostiene la garantía, porque el
  negocio no es responsable de IVA y no expide factura.
- **Los tres vigilantes con reclamo** —bandeja de revisión, sobrecostos de la transportadora y plazo
  de entrega vencido— tenían el mismo agujero, y sus javadoc lo llamaban "el lado por el que se
  prefiere fallar". Era una preferencia sin alternativa: con el adaptador tragando, no había otra.
  En sobrecostos es el peor de los tres, porque su reclamo se escribe con `do nothing` y **uno que
  no se devuelve no se vuelve a ganar jamás**: ese cobro de dinero solo aparecería en el extracto.
- **La vigilancia de saldo** devolvía `avisado = true` tanto si el correo salía como si no, con el
  campo documentado como "si salió el correo".

## Decisión

**El puerto lanza `CorreoNoEnviadoException` y cada caso de uso decide.** Tres respuestas, las tres
en uso y las tres escritas en el sitio donde se toman:

1. **Atraparla porque relanzarla haría daño.** `SolicitarRecuperacion` (anti-enumeración, arriba) y
   `RegistrarUsuario` (la cuenta, su autorización y su token ya están guardados).
2. **Atraparla y devolver el reclamo**, en las cuatro tareas que marcan "ya avisé" antes de mandar:
   el ciclo siguiente reintenta.
3. **Atraparla porque la operación pesa más que su aviso**, en los seis caminos del dinero.

**El adaptador registra además de lanzar**, y no es redundante: `application` no tiene slf4j en el
classpath —solo declara `:domain`, regla dura #1— así que un caso de uso que traga no puede dejar
constancia de nada. Ese registro es la única señal que queda de los dos casos del punto 1.

## La decisión de negocio, que el puerto llevaba meses dejando por tomar

El javadoc del puerto decía: *"decidir si además el envío debería poder tumbar la transacción en los
caminos del dinero es una decisión de negocio, no de programación, y no está tomada"*. Preguntado el
18 de septiembre, el negocio decidió: **la operación se guarda igual**.

El razonamiento, en el orden en que convence:

- **Un reintegro registrado que nadie comunicó es malo; un reintegro ejecutado del que no queda
  constancia es peor.** El dinero ya se movió, y la fila que lo demuestra sería la que se pierde.
- **Cancelar un pedido ya anuló las guías en Skydropx y devolvió el inventario**, y nada de eso lo
  revierte una transacción de base de datos. Un pedido "cancelado a medias" es peor estado que el de
  un comprador que se entera tarde.
- **Despachar ya emitió y cobró la guía**, y revertir no la desemite: quedaría un paquete que la
  transportadora recoge y entrega, de un pedido que el sistema cree en preparación.
- **El retracto es una fecha que la Ley 1480 mide.** Quien se retractó dentro de los cinco días
  hábiles se retractó, y perder esa constancia por un servidor de correo movería el trámite de día.

## Lo que esto NO cierra, y hay que decirlo sin adornos

**El correo sigue saliendo dentro de la transacción de quien llama y antes del commit.** Un fallo al
comprometer deja a quien compró leyendo "reintegramos el dinero de tu pedido" y al sistema sin
ninguna constancia de ese reintegro. Ningún `catch` arregla ese sentido: lo cierra una **bandeja de
salida** —guardar el correo en la misma transacción y mandarlo después, con reintentos—, que es un
mecanismo entero y no una línea, y no se construyó aquí.

**Y `RegistrarUsuario` deja una deuda concreta**: no existe un "reenviar verificación", así que una
cuenta creada el día que el SMTP falló se queda sin verificar hasta que su dueño lo pida por otro
canal.

## Dos cosas que enseñó, y las dos son sobre pruebas

**Una prueba fijaba el defecto, y estaba en verde.**
`EnviadorDeCorreoSpringMailFalloTest.unFalloAlEnviarSeRegistraYNoPropaga` exigía
`doesNotThrowAnyException()`. Lo que protegía era que el adaptador decidiera por sus doce
llamadores. Una prueba puede fijar un error tan bien como fija un acierto, y ninguna métrica de
cobertura distingue las dos cosas.

**Y los dobles hablaban un idioma que el adaptador no hablaba.** Los seis
`EnviadorDeCorreoFalso` lanzaban `IllegalStateException`, así que las pruebas que afirmaban "un
correo caído no deja la solicitud guardada a medias" comprobaban un escenario que producción no
podía producir — y una de ellas, `siElCorreoFallaNoQuedaUnaSolicitudSinAcuse`, afirmaba lo contrario
de lo que el sistema hacía. Ahora los dobles lanzan el tipo real. Es el mismo género del plugin de
capas que aceptaba la configuración sin aplicarla y del doble cuyo reclamo atómico era un
`Set.add()`: **el doble más barato es el que se parece lo bastante como para no probar nada.**

Las seis pruebas nuevas se comprobaron quitando la corrección a propósito: las tres de los
vigilantes fallan sin su `liberar`, y la del adaptador falla si vuelve a tragar.
