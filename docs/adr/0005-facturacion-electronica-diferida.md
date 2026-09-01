# ADR 0005. Facturación electrónica DIAN diferida

Fecha: 2026-08-30. Estado: aceptada.

## Contexto
La facturación electrónica ante la DIAN exige un proveedor tecnológico
autorizado, habilitación previa y un proceso administrativo con tiempos propios.

## Decisión
Se define el puerto `EmisorFacturaElectronica` con implementación nula en la fase
1. Las facturas se emiten por fuera con la herramienta que ya usa el negocio. El
pedido guarda desde ya todo lo que una factura necesita: desglose de IVA,
identificación del comprador y datos del vendedor.

## Consecuencias
Se puede abrir la tienda sin esperar la habilitación. Conectar un proveedor
después es escribir un adaptador, sin migrar datos ni recalcular pedidos viejos.
