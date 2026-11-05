# Valores no secretos de producción. Se versiona a propósito, como el de dev. Las cuentas de
# transferencia van aparte, en `cuentas.auto.tfvars`, que no se versiona.
#
# El primer apply va con todas las banderas en false: crea los recipientes de los secretos, la
# base y los servicios con la imagen de arranque de Google. Después se crea el usuario de la base,
# se cargan los secretos con gcloud y se encienden por tandas (`docs/07-infra-gcp.md`, "Arranque de
# producción").

# La cuenta de facturación, para el presupuesto. Vacía no lo crea. El monto va en la moneda de
# esa cuenta: unos US$30, o su equivalente en pesos si factura en COP.
cuenta_de_facturacion = "01D7F1-6B28DD-E50C88"
presupuesto_mensual   = 100000

# `pub_prod_...`, del panel de Wompi de producción.
wompi_llave_publica = "pub_prod_cOy5b1dUHRoyL5iX2ny3ZGquC3the5xa"

# El cliente de OAuth para entrar con Google, con `https://tecnosport.co` entre sus orígenes.
google_client_id = "110733132502-iktjhuu18vm9ncc2j0fiqcoe4le69kdn.apps.googleusercontent.com"

secretos_cargados  = true
sistecredito_listo = true
meta_listo         = true
ingesta_lista      = true

# Se enciende cuando el DNS de la raíz apunta aquí y el certificado está emitido.
dominio_activo = true
