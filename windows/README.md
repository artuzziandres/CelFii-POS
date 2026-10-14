# Cel-Fii Ventas para Windows

Aplicación de escritorio con caja, pagos combinados, productos, recuento de stock con comprobación de concurrencia, pedidos web, papelera recuperable, subida de fotos, historial e impresión por el controlador de Windows.

Usa el conector de la APK 1.2.0. La clave de conexión se ingresa desde Configuración y se guarda cifrada mediante Windows; no se incluye en el instalador.

## Impresora

Conectar la Global TP-POS58 por USB, instalar su controlador oficial y configurar el papel de 58 mm en Windows. Elegir la impresora en Configuración. La impresión abre el diálogo de Windows. El ticket se identifica como no fiscal.

## Facturación

Una venta no equivale a una factura. Se porta el flujo real de FiscalActivity de la APK: status, health, prepare, issue, document y attach. La emisión exige revisar receptor e importe y confirmar; pending recupera el mismo comprobante; authorized muestra número y CAE; external bloquea otra emisión. El PDF oficial del servidor se guarda o se abre en el lector de Windows para imprimir. La integración utiliza la misma clave de conexión de la APK. Una factura autorizada en producción todavía debe verificarse durante el uso real. No se emiten comprobantes fiscales durante las pruebas.

## Desarrollo

`npm install`, `npm test`, `npm start`, `npm run dist`.

Fuentes Electron: https://www.electronjs.org/docs/latest/tutorial/security
Instalador NSIS: https://www.electron.build/docs/nsis/
