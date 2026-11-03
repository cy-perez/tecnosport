variable "proyecto" {
  description = "Proyecto de GCP de producción. Nunca el de desarrollo: los dos ambientes no comparten ni cuentas, ni secretos, ni base."
  type        = string
  default     = "tecnosport-prod"
}

variable "region" {
  description = "La misma región que dev: us-east1 admite el mapeo de dominios de Cloud Run (ADR-0105) y las imágenes se promueven desde el registro de dev, que está aquí."
  type        = string
  default     = "us-east1"
}

variable "repositorio_github" {
  description = "El repositorio que puede desplegar, en formato duenio/nombre. La federación de identidad se acota a él, a `main` y al ambiente `produccion` de GitHub."
  type        = string
  default     = "cy-perez/tecnosport"
}

variable "ambiente_github" {
  description = "El ambiente de GitHub cuyo flujo puede autenticarse aquí. Ese ambiente exige la aprobación de una persona antes de correr, y esta condición es la que hace que saltárselo no sirva: un flujo sin el ambiente no recibe token."
  type        = string
  default     = "produccion"
}

variable "registro_de_dev" {
  description = "El registro de imágenes de dev, de donde se promueve lo que ya se probó allí. Producción no construye: copia la imagen del mismo SHA."
  type        = string
  default     = "us-east1-docker.pkg.dev/tecnosport-dev/contenedores"
}

variable "dominio" {
  description = "El dominio del sitio, sin esquema. Se mapea a la web junto con `www`."
  type        = string
  default     = "tecnosport.co"
}

variable "bucket_imagenes" {
  type    = string
  default = "tecnosport-prod-imagenes"
}

variable "bucket_proveedores" {
  type    = string
  default = "tecnosport-prod-proveedores"
}

variable "correo_admin" {
  description = "La cuenta de administración del panel y el remitente de los correos del sitio. Una sola dirección para todo, decidido el 3 de noviembre de 2026."
  type        = string
  default     = "contacto@tecnosport.co"
}

variable "correo_alertas" {
  description = "A dónde llegan el aviso de presupuesto y el de sitio caído."
  type        = string
  default     = "contacto@tecnosport.co"
}

variable "cuenta_de_facturacion" {
  description = "El ID de la cuenta de facturación (`XXXXXX-XXXXXX-XXXXXX`), para el presupuesto. Vacío no crea el presupuesto: crearlo pide permiso de administrador de facturación, y eso lo tiene una persona, no el proyecto."
  type        = string
  default     = ""
}

variable "presupuesto_mensual" {
  description = "El tope del aviso, **en la moneda de la cuenta de facturación** (la API no acepta otra). No corta nada: avisa al 50, al 90 y al 100 %. Unos US$30, o su equivalente en pesos si la cuenta factura en COP."
  type        = number
  default     = 0

  validation {
    condition     = var.cuenta_de_facturacion == "" || var.presupuesto_mensual > 0
    error_message = "Con cuenta_de_facturacion hace falta presupuesto_mensual, en la moneda de esa cuenta."
  }
}

variable "dominio_activo" {
  description = "Si el DNS de `tecnosport.co` ya apunta aquí y el certificado está emitido. Enciende la comprobación de la portada y su aviso: antes, fallarían en cada consulta."
  type        = bool
  default     = false
}

variable "frecuencia_del_tick" {
  description = "Cada minuto: la bandeja de salida, las emisiones y la recuperación de ingestas tienen ese intervalo (ADR-0104)."
  type        = string
  default     = "* * * * *"
}

variable "wompi_llave_publica" {
  description = "Llave pública de Wompi de producción (`pub_prod_...`). No es secreta. Vacía, la API arranca con el relleno que Wompi rechaza y el pago con tarjeta no funciona."
  type        = string
  default     = ""

  # Con los secretos montados la API arranca y el checkout ofrece el pago en línea: si la llave
  # falta o es la de pruebas, cada intento falla en el widget y aparta existencias media hora.
  # La aplicación no lo impide —solo rechaza una llave vacía, y vacía no llega: la reemplaza su
  # relleno—, así que lo impide el plan.
  validation {
    condition     = var.wompi_llave_publica == "" ? !var.secretos_cargados : startswith(var.wompi_llave_publica, "pub_prod_")
    error_message = "Con secretos_cargados = true hace falta la llave pública de producción de Wompi, y empieza por pub_prod_ (pub_test_ es la de pruebas)."
  }
}

variable "google_client_id" {
  description = "El ID de cliente de OAuth para entrar con Google. Vacío apaga la puerta: el sitio no pinta el botón. Ese cliente tiene que admitir el origen `https://tecnosport.co`."
  type        = string
  default     = ""
}

variable "db_usuario" {
  description = "El usuario con que la API y las migraciones entran a la base. Lo crea una persona con gcloud, con la clave que luego carga en `db-clave`: así la clave nunca pasa por el estado de Terraform."
  type        = string
  default     = "tecnosport"
}

# ── Banderas de arranque ──────────────────────────────────────────────────────────────────────
# Las mismas de dev y por la misma razón: montar `latest` de un secreto sin versiones deja la
# revisión sin arrancar, con un "internal error" que no nombra al secreto. Primero se aplica con la
# bandera en false para que exista el recipiente, se carga el valor con gcloud, y luego se enciende.

variable "secretos_cargados" {
  description = "Los de base, JWT, correo, administración, Wompi y Skydropx, más la clave del proxy y la llave del segundo factor. Sin ellos la API no arranca, y a propósito."
  type        = bool
  default     = false
}

variable "sistecredito_listo" {
  description = "Los tres de Sistecrédito. En producción el sandbox va apagado y no hay forma de encenderlo: cada transacción es un crédito real."
  type        = bool
  default     = false
}

variable "meta_listo" {
  description = "El token de Meta. Con él montado, el panel de producción publica en @tecnosport_co."
  type        = bool
  default     = false
}

variable "ingesta_lista" {
  description = "La clave de la API de Claude. Sin ella la ingesta extrae con el sembrado y todo borrador sale con alertas."
  type        = bool
  default     = false
}

variable "cuentas_de_transferencia" {
  description = "El número y el titular de las tres cuentas a las que el comprador transfiere (docs/11-pagos-y-envios.md). La entidad y el tipo no van aquí: son fijos y viven en `application.yml`. **Sin valor por omisión y fuera del repositorio**: el repo es público, y aunque el checkout enseña estos datos a quien hace un pedido, no es lo mismo que dejar el nombre y los celulares del dueño en el historial de GitHub. Se llena en `cuentas.auto.tfvars`, que no se versiona; la muestra está en `cuentas.auto.tfvars.example`. El titular es quien tiene la cuenta, no el nombre comercial: el negocio es de una persona natural (docs/08-seguridad-legal.md)."
  type = object({
    nequi     = object({ numero = string, titular = string })
    daviplata = object({ numero = string, titular = string })
    bbva      = object({ numero = string, titular = string })
  })

  # Un placeholder de la muestra copiado tal cual arrancaría sin quejarse y el checkout mandaría a
  # la gente a transferir a "pendiente". Y un valor vacío tampoco llega a fallar en Spring: el
  # `${...:default}` de `application.yml` lo cambia por su propio placeholder.
  validation {
    condition = alltrue([
      for cuenta in values(var.cuentas_de_transferencia) :
      trimspace(cuenta.numero) != "" && trimspace(cuenta.titular) != "" &&
      !strcontains(lower("${cuenta.numero} ${cuenta.titular}"), "pendiente")
    ])
    error_message = "Cada cuenta necesita número y titular reales: ni vacíos ni con los placeholders de cuentas.auto.tfvars.example."
  }
}
