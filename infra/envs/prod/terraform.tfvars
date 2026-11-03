# Valores no secretos de producción. Se versiona a propósito, como el de dev. Las cuentas de
# transferencia van aparte, en `cuentas.auto.tfvars`, que no se versiona.
#
# El primer apply va con todas las banderas en false: crea los recipientes de los secretos, la
# base y los servicios con la imagen de arranque de Google. Después se crea el usuario de la base,
# se cargan los secretos con gcloud y se encienden por tandas (`docs/07-infra-gcp.md`).

# La cuenta de facturación, para el presupuesto. Vacía no lo crea.
cuenta_de_facturacion = ""

# `pub_prod_...`, del panel de Wompi de producción.
wompi_llave_publica = ""

# El cliente de OAuth para entrar con Google, con `https://tecnosport.co` entre sus orígenes.
google_client_id = ""

secretos_cargados  = false
sistecredito_listo = false
meta_listo         = false
ingesta_lista      = false

# Se enciende cuando el DNS de la raíz apunta aquí y el certificado está emitido.
dominio_activo = false
