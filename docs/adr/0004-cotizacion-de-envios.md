# ADR 0004. Cotización de envíos detrás de un puerto

Fecha: 2026-08-30. Estado: **superada por `adr/0012`**.

## Contexto
Se pidió integrar Envía, Coordinadora e Interrapidísimo. Ninguna ofrece API
pública sin contrato comercial firmado, y cada una tiene su propio formato.

## Decisión
Se define el puerto `CotizadorEnvio` en `application`, con tres implementaciones
posibles:

1. Tabla de tarifas propia por destino, peso y volumen. Funciona el primer día
   sin depender de nadie. Es la de la fase 1.
2. Agregador con una sola integración que cubre varias transportadoras. Verificar
   cobertura, costos y condiciones antes de elegir.
3. API directa por transportadora, cuando el volumen justifique el contrato.

## Consecuencias
El checkout no se bloquea esperando un contrato comercial. Cambiar de estrategia
es escribir un adaptador, sin tocar el dominio ni el frontend. El peso y las
dimensiones de cada variante son obligatorios desde el día uno, porque las tres
estrategias los necesitan.

## Nota (2026-09-08)

`adr/0012` la superó al eliminar la cotización; `adr/0021` **retoma la idea de
este documento** —el puerto `CotizadorEnvio` con una implementación intercambiable—
y elige su estrategia 2: un agregador con una sola integración, Skydropx. Lo que
no vuelve es el código: el puerto se escribe de nuevo contra la API real, y las
tres estrategias de aquí siguen siendo el mapa de por dónde crecer.
