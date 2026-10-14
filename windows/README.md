# Cel-Fii Ventas para Windows

Aplicación de escritorio con caja, pagos combinados, productos, recuento de stock con comprobación de concurrencia, pedidos web, papelera recuperable, subida de fotos, historial e impresión por el controlador de Windows.

Usa el conector de la APK 1.2.0. La clave de conexión se ingresa desde Configuración y se guarda cifrada mediante Windows; no se incluye en el instalador.

## Impresora

Conectar la Global TP-POS58 por USB, instalar su controlador oficial y configurar el papel de 58 mm en Windows. Elegir la impresora en Configuración. La impresión abre el diálogo de Windows. El ticket se identifica como no fiscal.

## Facturación

Una venta no equivale a una factura. El código recuperado de la APK 1.2.0 solo muestra una vista previa sin CAE. La integración fiscal debe verificarse contra el módulo real del servidor antes de afirmar que una factura fue autorizada. No se emiten comprobantes fiscales durante las pruebas.

## Desarrollo

`npm install`, `npm test`, `npm start`, `npm run dist`.

Fuentes Electron: https://www.electronjs.org/docs/latest/tutorial/security
Instalador NSIS: https://www.electron.build/docs/nsis/
