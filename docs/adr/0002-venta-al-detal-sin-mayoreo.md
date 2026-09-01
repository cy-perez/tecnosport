# ADR 0002. Fase 1 solo al detal, con pago en línea

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
El negocio vende al mayor y al detal. El kit de interfaz se diseñó originalmente
para vitrina más cotización, sin carrito. El brief de desarrollo pide ecommerce
con pago en línea.

## Decisión
La fase 1 es venta al detal con carrito y pago. El mayoreo, las listas de precio
por cliente y las cotizaciones quedan fuera y no se construyen anticipadamente.

## Consecuencias
El componente de lista de cotización del kit se reinterpreta como carrito. El
modelo de datos no bloquea el mayoreo futuro: la variante puede recibir precios
por lista sin migración destructiva. Pero no hay código muerto esperándolo.
