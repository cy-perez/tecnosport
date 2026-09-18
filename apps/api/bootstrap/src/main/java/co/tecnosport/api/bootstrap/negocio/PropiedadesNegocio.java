package co.tecnosport.api.bootstrap.negocio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del negocio que cambian lo que el sistema puede cobrar, no cómo se despliega.
 *
 * <p>{@code responsableDeIva} dice si TecnoSport es responsable del impuesto sobre las ventas. Hoy
 * no lo es (parágrafo 3 del art. 437 del Estatuto Tributario), y eso no es una preferencia: el
 * literal a del art. 1.3.1.15.2 del Decreto 1625 de 2016 le prohíbe a un no responsable adicionar
 * al precio suma alguna por concepto de IVA, y quien lo hace queda obligado a cumplir íntegramente
 * el régimen de los responsables desde ese día. Por eso la condición vive aquí y no en un campo del
 * formulario del panel: una tasa distinta de cero tecleada por descuido no es un dato malo, es un
 * cambio de régimen tributario.
 *
 * <p><strong>Es reversible y va a revertirse algún día.</strong> La calidad de no responsable exige
 * cumplir todas las condiciones del parágrafo 3 a la vez —entre ellas un tope anual de ingresos
 * brutos en UVT— y se pierde desde el período siguiente a incumplir cualquiera. El día que eso
 * pase, esta propiedad pasa a {@code true} y con ella hay que mover el texto publicado y las tasas
 * del catálogo. {@code npm run datos-negocio} comprueba que las dos cosas digan lo mismo, y {@code
 * adr/0041} escribe qué más hay que tocar.
 */
@ConfigurationProperties(prefix = "tecnosport.negocio")
public record PropiedadesNegocio(boolean responsableDeIva) {}
