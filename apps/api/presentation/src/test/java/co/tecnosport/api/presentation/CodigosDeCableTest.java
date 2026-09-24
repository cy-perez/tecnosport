package co.tecnosport.api.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.tecnosport.api.application.atencion.SolicitudAtencionNoEncontradaException;
import co.tecnosport.api.application.catalogo.AtributoNoEncontradoException;
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
      Map.of(
          ArticuloSinMedidasException.class, "ARTICULO_SIN_MEDIDAS",
          ArticuloNoAsegurableException.class, "ARTICULO_NO_ASEGURABLE",
          EnvioSinCoberturaException.class, "ENVIO_SIN_COBERTURA",
          CotizacionRechazadaException.class, "COTIZACION_RECHAZADA",
          ExistenciaInsuficienteException.class, "EXISTENCIA_INSUFICIENTE",
          CredencialesInvalidasException.class, "CREDENCIALES_INVALIDAS",
          SesionDeRefrescoInvalidaException.class, "SESION_DE_REFRESCO_INVALIDA",
          LimiteDeIntentosExcedidoException.class, "LIMITE_DE_INTENTOS_EXCEDIDO",
          SistecreditoNoEntregoLaUrlDePagoException.class, "SISTECREDITO_NO_ENTREGO_LA_URL_DE_PAGO",
          MontoDeReintegroInvalidoException.class, "MONTO_DE_REINTEGRO_INVALIDO");

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
