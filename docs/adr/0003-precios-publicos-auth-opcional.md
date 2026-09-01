# ADR 0003. Precios públicos y autenticación opcional

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
Se planteó eliminar la autenticación al no haber mayoreo.

## Decisión
Los precios son públicos y la compra se completa como invitado. La autenticación
no se elimina: es obligatoria para el panel administrativo y el asistente de
captura, y opcional para el cliente, que puede crear cuenta al final del checkout.

## Consecuencias
Menos fricción al detal. Se mantiene la infraestructura de sesión, que la app
móvil y el mayoreo futuro necesitarán de todos modos.
