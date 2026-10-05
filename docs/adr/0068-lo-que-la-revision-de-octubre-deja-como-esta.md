# ADR-0068 — Lo que la revisión adversarial de octubre deja como está, y por qué

**Fecha:** 2026-10-04
**Estado:** aceptado.

## Contexto

La revisión adversarial del 4 de octubre de 2026 levantó unos ochenta hallazgos
y casi todos se corrigieron en la misma rama. Cuatro se dejan como están a
propósito. Esta ADR los escribe para que la próxima revisión no los vuelva a
levantar como si nadie los hubiera visto, y para que quien los quiera cambiar
sepa qué se pesó.

## Decisiones

### 1. Los ciclos entre contextos se aceptan, por ahora

Hay dependencias en los dos sentidos entre `catalogo` y `proveedores` en
`domain`, y entre `catalogo` y `pedido`, `pedido` y `envio`, y `reintegro` y
`reversion` en `application`. Ningún documento del proyecto los prohíbe:
`01-arquitectura.md` fija la dirección entre **capas**, no entre contextos.

Un monolito modular con ciclos no se puede partir por contexto. Partirlo no está
en el plan de ningún horizonte que se conozca, y romper los ciclos hoy sería
mover código sin ganancia que se pueda medir. **Si algún día se parte un
contexto en un servicio aparte, este es el primer trabajo**, y ArchUnit tiene la
regla para impedir que crezcan: hoy no se escribe porque fallaría con lo que ya
existe.

### 2. Un controlador puede leer un repositorio para una consulta pura

Siete controladores del panel (`AdminGarantias`, `AdminReversiones`,
`AdminRetractos`, `AdminDifusion`, `AdminProveedor`, `AdminBorrador`,
`AdminIngesta`) leen un puerto de repositorio sin pasar por un caso de uso. Lo
hacen solo para leer, sin regla de negocio en medio. Envolver cada lectura en un
caso de uso que solo delega duplica clases sin proteger nada.

**El límite queda escrito:** en el momento en que una lectura necesite un filtro
de autorización o de visibilidad, pasa a un caso de uso. Ningún controlador
escribe por un repositorio: las escrituras van siempre por un caso de uso, y la
compensación de la cola de ingestas, que era la excepción, ya pasó a
`EncolarIngesta`.

### 3. Los mapeadores de presentación que consultan quedan como están

`MapeadorRespuestasPedido` y `MapeadorSeguimiento` consultan envíos y el tope
de reintegro por cada pedido, fuera de la transacción del caso de uso. En la
lista del panel son unas cuarenta consultas por página de veinte. Con el
volumen de pedidos de hoy no se nota, y moverlo bien —la composición dentro del
caso de uso, en una sola lectura— toca el contrato de varias respuestas.

**Se rehace cuando la lista del panel pase de un segundo**, o cuando la app
móvil necesite la misma composición.

### 4. `MapeadorCatalogo` sigue recortando el agregado

El mapeador JPA descarta las variantes que no están `ACTIVA` y los sets de
rotación que no están `PUBLICADO`, también para el panel. Eso hace imposible
reactivar una variante inactiva desde el panel, y deja como código muerto la
guarda de `CrearPedido`.

Separar la hidratación de la vitrina de la del panel toca todas las consultas del
catálogo y la carga de fotos, y hoy ningún flujo del negocio inactiva una
variante para reactivarla después: se borra o se deja. **Es la deuda de mayor
riesgo de las cuatro**, y la primera que hay que pagar el día que el panel
ofrezca "desactivar variante".

## Consecuencias

La próxima revisión adversarial puede citar esta ADR en vez de reabrir estos
cuatro puntos, salvo que haya cambiado el supuesto de alguno: que se parta un
contexto, que una lectura necesite autorización, que la lista del panel sea
lenta o que se pueda desactivar una variante.
