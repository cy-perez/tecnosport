# Arquitectura

## Monorepo

Un solo repositorio en GitHub con todo: backend, frontend, kit de marca,
contratos generados, infraestructura y documentación.

```
apps/api           Gradle multi-módulo. Su propio settings.gradle.kts.
apps/web           Angular. Workspace de npm.
packages/marca     Kit de marca. Workspace de npm.
packages/contratos Cliente generado del OpenAPI. Workspace de npm.
infra              Terraform.
tools              Scripts de apoyo, en Node.
docs               Documentación.
```

La raíz declara los workspaces de npm para todo lo de JavaScript. Gradle vive
autocontenido dentro de `apps/api` y no se mezcla con npm.

Razones de esta decisión, y sus consecuencias, en `adr/0007`.

En integración continua los cambios se detectan por ruta: un cambio que solo toca
`apps/web` no reconstruye el backend. Las pruebas de contrato sí corren siempre,
porque son justamente las que detectan que una parte se desincronizó de la otra.

## Backend: capas

```
bootstrap          main, wiring de Spring, perfiles. Depende de todos.
   |
   +-- presentation      REST, DTO
   +-- infrastructure    JPA, Wompi, GCS, correo, Flyway
            |
        application       casos de uso y PUERTOS
            |
          domain          Java puro
```

| Módulo | Puede depender de | Nunca de |
|---|---|---|
| `domain` | nada | todo lo demás |
| `application` | `domain` | infraestructura, presentación, Spring web, JPA |
| `infrastructure` | `application`, `domain` | `presentation` |
| `presentation` | `application`, `domain` | `infrastructure` |
| `bootstrap` | todos | nada |

`presentation` no depende de `infrastructure`: el controlador llama al caso de
uso y quién implementa el repositorio lo decide `bootstrap`. Si un controlador
necesita algo de infraestructura, falta un caso de uso.

Esto lo verifica ArchUnit, no la buena voluntad. La prueba vive en
`bootstrap/src/test/java/.../ArquitecturaTest.java` y falla el build.

### Puertos declarados en `application`

| Puerto | Adaptador de producción | Adaptador de prueba |
|---|---|---|
| `RepositorioProductos`, `RepositorioPedidos`, y demás | JPA con PostgreSQL | en memoria |
| `PasarelaDePagos` | Wompi | falsa determinista |
| `CotizadorEnvio` | tarifas propias o agregador | tabla fija |
| `RecaudoContraentrega` | transportadora con recaudo | espía |
| `EmisorFacturaElectronica` | ninguno en fase 1 | espía |
| `AlmacenDeImagenes` | Cloud Storage con URL firmada | sistema de archivos |
| `EnviadorDeCorreo` | proveedor SMTP | recolector en memoria |
| `Reloj` | reloj del sistema | reloj fijo |

`Reloj` como puerto no es exceso: sin él no se prueban vencimientos de reserva ni
expiraciones de token sin dormir el hilo.

## Frontend: las mismas capas

Por funcionalidad, y dentro de cada una: `domain`, `application`,
`infrastructure`, `presentation`. El `domain` del frontend no es una copia del
backend: es el modelo que la interfaz necesita. El mapeo de DTO a modelo vive en
`infrastructure` y es el único lugar que conoce la forma de la respuesta HTTP.

Un componente inyecta `RepositorioProductos`, la interfaz declarada en `domain`,
no `ProductosHttpService`. El proveedor se declara en la ruta. Con eso, una
prueba de componente no necesita interceptar HTTP.

Las APIs del navegador (cámara, sensores, canvas) se tratan como infraestructura:
detrás de un puerto, con implementación falsa para pruebas. Detalle en
`10-captura-360.md`.

## Qué no es esta arquitectura

- **No son microservicios.** Es un monolito modular desplegado como un servicio.
  Con un desarrollador y este volumen, los microservicios solo agregan latencia y
  operación.
- **No es CQRS ni event sourcing.** Consultas y comandos comparten modelo. Si una
  consulta pide una proyección incómoda, se hace una consulta de lectura en
  `infrastructure` que devuelve un DTO sin pasar por el agregado. Eso está
  permitido y documentado; inventar un bus de eventos no lo está.
- **No es hexagonal con cuarenta interfaces de una sola implementación.** Un
  puerto existe cuando hay algo externo al otro lado. Una clase con una sola
  implementación interna no es un puerto: es una clase.

## Decisiones registradas

En `adr/`. Si cambias algo de este documento, escribe el ADR.
