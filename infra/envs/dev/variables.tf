variable "proyecto" {
  description = "Proyecto de GCP del ambiente de desarrollo. Nunca el de producción."
  type        = string
  default     = "tecnosport-dev"
}

variable "region" {
  description = "Una sola región para todo el ambiente. us-east1 porque ahí está el bucket de imágenes que ya existía, y es una de las tres que admite la capa gratuita de Storage."
  type        = string
  default     = "us-east1"
}

variable "repositorio_github" {
  description = "El repositorio que puede desplegar, en formato duenio/nombre. La federación de identidad se acota a él: sin esa condición, cualquier repositorio de GitHub podría pedir un token de esta cuenta."
  type        = string
  default     = "cy-perez/tecnosport"
}

variable "rama_que_despliega" {
  description = "Solo esta referencia puede desplegar. Un pull request de un desconocido no toca la infraestructura."
  type        = string
  default     = "refs/heads/main"
}

variable "bucket_imagenes" {
  description = "Bucket de imágenes de producto de este ambiente, declarado y administrado aquí desde el 23 de septiembre de 2026 (ADR-0058). El de local es otro y lo crea infra/local/bucket-imagenes.mjs: compartirlos dejó 348 objetos de esa máquina aquí dentro."
  type        = string
  default     = "tecnosport-dev-imagenes"
}

variable "dominio_publico_web" {
  description = "Origen público del sitio. Se llena después del primer apply, cuando Cloud Run asigna la URL, y se vuelve a aplicar: APP_URL_PUBLICA y NG_ALLOWED_HOSTS lo necesitan."
  type        = string
  default     = ""
}

variable "secretos_cargados" {
  description = "Si los secretos de Secret Manager ya tienen al menos una versión. Mientras sea false, los servicios no los montan: un secreto vacío hace que la revisión no arranque, y Cloud Run lo reporta como un error interno que no menciona los secretos."
  type        = bool
  default     = false
}

variable "db_host" {
  description = "Punto de conexión de Neon. Vacío mientras no exista: con esto vacío, el servicio no recibe ninguna variable de base de datos, que es mejor que recibirlas a medias."
  type        = string
  default     = ""
}

variable "db_nombre" {
  type    = string
  default = ""
}

variable "db_usuario" {
  type    = string
  default = ""
}

variable "db_params" {
  description = "Parámetros de la URL de JDBC. Neon **exige** TLS, y sin esto la conexión se rechaza; la plantilla de application.yml no tenía dónde ponerlos."
  type        = string
  default     = "?sslmode=require"
}

variable "wompi_llave_publica" {
  description = "Llave pública de Wompi sandbox (`pub_test_...`). No es secreta —el navegador la recibe para abrir el checkout— así que va aquí y no en Secret Manager. Vacía mientras no se tenga: la aplicación arranca igual, con un valor de relleno que Wompi rechaza, y el pago con tarjeta no funciona."
  type        = string
  default     = ""
}

variable "dominio_publico_api" {
  description = "Origen público del servicio de la API. Se llena después del primer apply, igual que `dominio_publico_web`, y no se puede derivar de `module.api.url` dentro del propio módulo: Terraform lo ve como un ciclo. Lo necesita la URL de confirmación de Sistecrédito, que la pasarela llama desde fuera."
  type        = string
  default     = ""
}

variable "sistecredito_listo" {
  description = "Si los tres secretos de Sistecrédito ya tienen versión cargada. Mientras sea false, el método arranca apagado y los secretos no se montan — que es la misma secuencia de `secretos_cargados` y por el mismo motivo doble: montar `latest` de un secreto sin versiones hace que la revisión no arranque, y encender el método sin sus credenciales tampoco arranca, esta vez a propósito (PropiedadesSistecredito se niega). Orden: apply, cargar los tres valores con gcloud, poner esto en true, volver a aplicar."
  type        = bool
  default     = false
}

variable "correo_admin" {
  description = "La cuenta de administración del panel. No es secreta —la clave sí, y vive en Secret Manager—. Estaba fijada a mano en el servicio de Cloud Run y no en esta configuración, así que el primer apply que tocara el servicio la habría borrado."
  type        = string
  default     = "contacto@tecnosport.co"
}

variable "sistecredito_sandbox" {
  description = "El freno de seguridad, desde Terraform. `true` pide a la pasarela que simule el estado de `estado_simulado_sistecredito` sin cobrarle a nadie; **`false` significa que cada compra con Sistecrédito en dev abre un crédito real a nombre de una persona de verdad**, porque esta cuenta solo tiene credenciales productivas y la pasarela es `api.credinet.co` también desde aquí. Se pone en `false` para una prueba con fecha y se devuelve a `true` el mismo día (adr/0048)."
  type        = bool
  default     = true
}

variable "estado_simulado_sistecredito" {
  description = "Qué estado simula la pasarela cuando el sandbox está encendido. La lista es la que el propio `SistecreditoClient` sabe interpretar, y se valida aquí porque un valor mal escrito no falla: viaja tal cual y la pasarela hace otra cosa."
  type        = string
  default     = "Approved"

  validation {
    condition = contains(
      ["Approved", "Rejected", "Cancelled", "Expired", "Abandoned", "Failed", "Pending", "PendingForPaymentMethod"],
      var.estado_simulado_sistecredito
    )
    error_message = "Estado simulado desconocido. Los que el cliente interpreta son Approved, Rejected, Cancelled, Expired, Abandoned, Failed, Pending y PendingForPaymentMethod."
  }
}

variable "ruta_confirmacion_sistecredito" {
  description = "La ruta pública a la que Sistecrédito manda la notificación, colgando de `dominio_publico_api`. Es variable por una sola razón, y no es de producción: apuntarla a una ruta que no existe es la única forma honesta de ensayar que la conciliación recoge un pago cuyo aviso nunca llegó — que es lo que le pasa al comprador que cierra la ventana. Se devuelve a su valor en cuanto la prueba termina."
  type        = string
  default     = "/api/v1/pagos/sistecredito/confirmacion"
}
