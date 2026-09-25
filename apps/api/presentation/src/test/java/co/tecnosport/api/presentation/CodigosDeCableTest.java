package co.tecnosport.api.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.atencion.SolicitudAtencionNoEncontradaException;
import co.tecnosport.api.application.catalogo.AtributoNoEncontradoException;
import co.tecnosport.api.application.catalogo.CategoriaConHijasException;
import co.tecnosport.api.application.catalogo.CategoriaConProductosException;
import co.tecnosport.api.application.catalogo.CategoriaNoEsHojaException;
import co.tecnosport.api.application.catalogo.CategoriaSlugYaExisteException;
import co.tecnosport.api.application.catalogo.CicloDeCategoriasException;
import co.tecnosport.api.application.catalogo.ProductoConVentasException;
import co.tecnosport.api.application.catalogo.ProductoPublicadoException;
import co.tecnosport.api.application.catalogo.ProfundidadDeCategoriaExcedidaException;
import co.tecnosport.api.application.compartido.LimiteDeIntentosExcedidoException;
import co.tecnosport.api.application.envio.AcuseNoAplicableException;
import co.tecnosport.api.application.envio.ArticuloNoAsegurableException;
import co.tecnosport.api.application.envio.ArticuloSinMedidasException;
import co.tecnosport.api.application.envio.CotizacionRechazadaException;
import co.tecnosport.api.application.envio.EmisionNoEncontradaException;
import co.tecnosport.api.application.envio.EnvioSinCoberturaException;
import co.tecnosport.api.application.envio.GuiaNoEncontradaException;
import co.tecnosport.api.application.garantia.LineaNoEsDelPedidoException;
import co.tecnosport.api.application.pago.SistecreditoNoEntregoLaUrlDePagoException;
import co.tecnosport.api.application.pedido.ContraentregaNoDisponibleException;
import co.tecnosport.api.application.pedido.MetodoDePagoNoEsTransferenciaManualException;
import co.tecnosport.api.application.reintegro.MontoDeReintegroInvalidoException;
import co.tecnosport.api.application.reintegro.ReintegroRequeridoException;
import co.tecnosport.api.application.retracto.PedidoSinEntregarException;
import co.tecnosport.api.application.retracto.RetractoYaRadicadoException;
import co.tecnosport.api.application.usuario.CredencialesInvalidasException;
import co.tecnosport.api.application.usuario.SesionDeRefrescoInvalidaException;
import co.tecnosport.api.domain.catalogo.GaleriaLlenaException;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaDuplicadaException;
import co.tecnosport.api.domain.catalogo.ImagenDeGaleriaNoEncontradaException;
import co.tecnosport.api.domain.catalogo.ProductoSinImagenPrincipalException;
import co.tecnosport.api.domain.compartido.ExcepcionDeDominio;
import co.tecnosport.api.domain.inventario.ExistenciaInsuficienteException;
import co.tecnosport.api.domain.pedido.TransicionDeEstadoInvalidaException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Los códigos de cable que el frontend conoce como literales, fijados uno a uno.
 *
 * <p><b>Del lado del servidor no los escribe nadie</b>: {@code ManejadorDeErrores} los deriva del
 * <em>nombre de la clase</em> de la excepción, para no mantener un catálogo aparte por cada regla
 * nueva. Esa decisión está bien y se queda; lo que faltaba es la otra mitad: renombrar una
 * excepción cambia el contrato publicado <b>sin tocar una sola cadena</b>, y hasta hoy la suite
 * entera se quedaba en verde mientras el comprador empezaba a ver un mensaje genérico.
 *
 * <p>La demostración de que hacía falta: renombra {@code ArticuloSinMedidasException} a {@code
 * ArticuloSinMedirException}. Compila, todo pasa, y el código pasa a ser {@code
 * ARTICULO_SIN_MEDIR}: la rama de {@code envio-http.repositorio.ts:101} muere, el {@code catch} cae
 * al {@code throw} genérico, y a quien lleva un artículo sin medir se le enseña un fallo cualquiera
 * en vez de ofrecerle la recogida en el punto. Con esta prueba, ese renombrado se para aquí.
 *
 * <p>Los de arriba los cablea un adaptador del frontend; los de abajo, una clave de traducción en
 * los JSON del panel ({@code admin.errores.*}), que es lo mismo: si el código cambia, {@code
 * mensaje-de-error.ts} no encuentra la clave y cae al mensaje genérico de la pantalla, en silencio.
 */
class CodigosDeCableTest {

  /** Los que decide un adaptador del frontend, no una traducción. */
  private static final Map<Class<? extends Exception>, String> CABLEADOS_EN_ADAPTADORES =
      Map.ofEntries(
          Map.entry(ArticuloSinMedidasException.class, "ARTICULO_SIN_MEDIDAS"),
          Map.entry(ArticuloNoAsegurableException.class, "ARTICULO_NO_ASEGURABLE"),
          Map.entry(EnvioSinCoberturaException.class, "ENVIO_SIN_COBERTURA"),
          Map.entry(CotizacionRechazadaException.class, "COTIZACION_RECHAZADA"),
          Map.entry(ExistenciaInsuficienteException.class, "EXISTENCIA_INSUFICIENTE"),
          Map.entry(CredencialesInvalidasException.class, "CREDENCIALES_INVALIDAS"),
          Map.entry(SesionDeRefrescoInvalidaException.class, "SESION_DE_REFRESCO_INVALIDA"),
          Map.entry(LimiteDeIntentosExcedidoException.class, "LIMITE_DE_INTENTOS_EXCEDIDO"),
          Map.entry(
              SistecreditoNoEntregoLaUrlDePagoException.class,
              "SISTECREDITO_NO_ENTREGO_LA_URL_DE_PAGO"),
          Map.entry(MontoDeReintegroInvalidoException.class, "MONTO_DE_REINTEGRO_INVALIDO"),
          // Los seis rechazos del árbol de categorías, que `categorias-admin-http.repositorio.ts`
          // traduce a un resultado con nombre para que la pantalla diga qué hacer: "mueve primero
          // sus productos", "borra antes sus subcategorías". Sin esto, renombrar la excepción deja
          // el panel enseñando el fallo genérico y la suite en verde.
          Map.entry(CategoriaSlugYaExisteException.class, "CATEGORIA_SLUG_YA_EXISTE"),
          Map.entry(CategoriaConProductosException.class, "CATEGORIA_CON_PRODUCTOS"),
          Map.entry(CategoriaConHijasException.class, "CATEGORIA_CON_HIJAS"),
          Map.entry(CategoriaNoEsHojaException.class, "CATEGORIA_NO_ES_HOJA"),
          Map.entry(
              ProfundidadDeCategoriaExcedidaException.class, "PROFUNDIDAD_DE_CATEGORIA_EXCEDIDA"),
          Map.entry(CicloDeCategoriasException.class, "CICLO_DE_CATEGORIAS"));

  /** Los que tienen una clave de traducción en `admin.errores.*`. */
  private static final Map<Class<? extends Exception>, String> CABLEADOS_EN_TRADUCCIONES =
      Map.ofEntries(
          Map.entry(AcuseNoAplicableException.class, "ACUSE_NO_APLICABLE"),
          Map.entry(ContraentregaNoDisponibleException.class, "CONTRAENTREGA_NO_DISPONIBLE"),
          Map.entry(EmisionNoEncontradaException.class, "EMISION_NO_ENCONTRADA"),
          Map.entry(ExcepcionDeDominio.class, "EXCEPCION_DE_DOMINIO"),
          Map.entry(GaleriaLlenaException.class, "GALERIA_LLENA"),
          Map.entry(GuiaNoEncontradaException.class, "GUIA_NO_ENCONTRADA"),
          Map.entry(ImagenDeGaleriaDuplicadaException.class, "IMAGEN_DE_GALERIA_DUPLICADA"),
          Map.entry(ImagenDeGaleriaNoEncontradaException.class, "IMAGEN_DE_GALERIA_NO_ENCONTRADA"),
          Map.entry(LineaNoEsDelPedidoException.class, "LINEA_NO_ES_DEL_PEDIDO"),
          Map.entry(
              MetodoDePagoNoEsTransferenciaManualException.class,
              "METODO_DE_PAGO_NO_ES_TRANSFERENCIA_MANUAL"),
          Map.entry(PedidoSinEntregarException.class, "PEDIDO_SIN_ENTREGAR"),
          Map.entry(ProductoSinImagenPrincipalException.class, "PRODUCTO_SIN_IMAGEN_PRINCIPAL"),
          // Los dos rechazos del borrado de un producto: el panel los traduce a "retíralo primero"
          // y a "tiene ventas, retíralo en vez de borrarlo", que son instrucciones distintas. Con
          // el mensaje genérico, quien borra no sabe cuál de las dos le toca.
          Map.entry(ProductoPublicadoException.class, "PRODUCTO_PUBLICADO"),
          Map.entry(ProductoConVentasException.class, "PRODUCTO_CON_VENTAS"),
          Map.entry(ReintegroRequeridoException.class, "REINTEGRO_REQUERIDO"),
          Map.entry(RetractoYaRadicadoException.class, "RETRACTO_YA_RADICADO"),
          Map.entry(TransicionDeEstadoInvalidaException.class, "TRANSICION_DE_ESTADO_INVALIDA"),
          Map.entry(AtributoNoEncontradoException.class, "ATRIBUTO_NO_ENCONTRADO"),
          Map.entry(
              SolicitudAtencionNoEncontradaException.class, "SOLICITUD_ATENCION_NO_ENCONTRADA"));

  @Test
  void losCodigosQueUnAdaptadorDelFrontendCableaNoCambian() {
    CABLEADOS_EN_ADAPTADORES.forEach(
        (clase, esperado) ->
            assertEquals(
                esperado,
                ManejadorDeErrores.codigoDesde(clase),
                "el código de cable de " + clase.getSimpleName() + " cambió"));
  }

  @Test
  void losCodigosQueTienenTraduccionEnElPanelNoCambian() {
    CABLEADOS_EN_TRADUCCIONES.forEach(
        (clase, esperado) ->
            assertEquals(
                esperado,
                ManejadorDeErrores.codigoDesde(clase),
                "el código de cable de " + clase.getSimpleName() + " cambió"));
  }
}
