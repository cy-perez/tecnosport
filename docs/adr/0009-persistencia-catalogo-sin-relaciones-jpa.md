# ADR 0009. Persistencia del catálogo sin relaciones JPA entre entidades

Fecha: 2026-09-02. Estado: aceptada.

## Contexto

`Producto` es un agregado de dominio rico: variantes, cada una con sus pares
atributo-valor, imágenes, y un set de rotación que puede pertenecer al
producto o a una variante concreta. Hidratarlo desde PostgreSQL con
`@ManyToOne`/`@OneToMany` obliga a que la forma del grafo JPA coincida con la
forma del agregado de dominio, y ambas cosas no siempre calzan (el set de
rotación, por ejemplo, cuelga de `producto_id` con `variante_id` opcional,
no de una jerarquía fija).

## Decisión

Cada entidad JPA en `infrastructure/catalogo/entidad` es un espejo plano de su
tabla: columnas y claves foráneas como `UUID` sueltos, sin relaciones
declaradas. `MapeadorCatalogo` hace las consultas por lote que hacen falta
(`findAllById`, `findByProductoIdIn`...) y arma el `Producto` de dominio a
mano, reutilizando los constructores completos que `Producto`, `Variante` y
`SetRotacion` ya exponen para reconstrucción (ver `domain`). La única
excepción es `AtributoJpaEntity.valoresPermitidos`, un `@ElementCollection`
`EAGER`: es una lista de `String`, no una relación entre entidades.

`RepositorioProductosJpa.buscar(...)` resuelve la página de resultados en dos
pasos: una consulta nativa (`JdbcTemplate`) que solo toca `producto`/
`variante` para filtrar, ordenar y paginar por cursor, y después
`MapeadorCatalogo` hidrata solo los `id` de esa página.

## Alternativas

Grafos JPA con `@EntityGraph`/fetch join: menos código de mapeo a primera
vista, pero el riesgo real es N+1 implícito y colecciones perezosas
inicializadas fuera de sesión — ya pasó una vez en este mismo paso con
`valoresPermitidos` antes de marcarla `EAGER`. Para un agregado con esta
cantidad de tablas relacionadas, preferible pagar el costo de las consultas
explícitas que perseguir un `LazyInitializationException` intermitente.

## Consecuencias

Más consultas por hidratación (una por tabla relacionada, por lote, nunca por
fila), pero predecibles y fáciles de seguir en una prueba con Testcontainers.
Este patrón es el que se sigue para cualquier agregado nuevo con esta forma
(candidato claro: `Pedido` en Fase 3, con sus líneas congeladas e historial).
