/**
 * El id de carrito guardado en el navegador ya no existe en el servidor: lo borró la purga de
 * carritos inactivos, o la base se reinició. No es un fallo del comprador ni de la red, y el store
 * lo resuelve solo con un carrito nuevo — por eso se distingue de un error genérico.
 */
export class CarritoInexistenteError extends Error {}
