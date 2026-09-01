# packages/contratos

Cliente TypeScript generado desde el contrato OpenAPI del backend. Es código
generado: no se edita a mano.

## Regenerar

Con el backend corriendo en local:

```
npm run contratos
```

Lee `http://localhost:8080/api/openapi.json` y produce los tipos y las funciones
de llamada en `src/`.

En integración continua el contrato se genera desde el build del backend, sin
levantar el servidor.

## Uso

`apps/web` importa estos tipos solo dentro de la capa `infrastructure` de cada
funcionalidad, donde se mapean a los modelos de dominio del frontend. Ningún
componente ve un DTO generado.

## Cuando cambia la API

1. Se cambia el backend.
2. Se regenera este paquete.
3. TypeScript rompe en los mapeadores afectados. Esa es la señal, y es
   deliberada: un cambio de contrato tiene que fallar en compilación, no en
   producción.
