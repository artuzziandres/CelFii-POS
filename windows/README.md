# Cel-Fii Ventas para Windows

Aplicación nativa para Windows 11 x64 conectada al mismo Google Sheets de Cel-Fii Ventas.

La interfaz 1.1.1 está optimizada para pantallas de escritorio: navegación amplia,
panel de ticket fijo, columnas sin desplazamiento horizontal y acciones visibles.

## Impresora Global TP-POS58 por USB

1. Instalar en Windows el controlador de la impresora.
2. Conectarla por USB y confirmar que aparezca en **Configuración > Bluetooth y dispositivos > Impresoras y escáneres**.
3. Abrir Cel-Fii Ventas, entrar a **Más**, elegir la impresora y guardar.
4. Usar **Guardar e imprimir** al cerrar la venta.

Los lectores USB de códigos de barras funcionan como teclado: dejar el cursor en el buscador y escanear.

El token se guarda solamente en `%LocalAppData%\CelFiiVentas\settings.json` y no forma parte del ejecutable.
