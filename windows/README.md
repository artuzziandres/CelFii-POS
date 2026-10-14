# Cel-Fii Ventas para Windows

Aplicación de escritorio con caja, pagos combinados, productos, recuento de stock con comprobación de concurrencia, pedidos web, papelera recuperable, subida de fotos, historial e impresión por el controlador de Windows.

Usa el conector de la APK 1.2.0. La clave de conexión se ingresa desde Configuración y se guarda cifrada mediante Windows; no se incluye en el instalador.

## Versión 1.0.1

Tarjetas con fotos, galería de imágenes, menú sin numeración, catálogo paginado completo y ticket con cantidad, precio unitario y subtotal identificados. La lista autenticada conserva sus precios y stock; las fotos públicas se combinan únicamente por ID. Si una foto no tiene URL pública o falla, se consulta el servicio de fotos autenticado. Se cargan solo las imágenes cercanas a la vista, con un máximo de tres consultas de respaldo a la vez.

La pistola USB debe funcionar como teclado y enviar Enter al final del código. Caja enfoca automáticamente la búsqueda, reconoce código principal y alternativo, conserva ceros iniciales y prepara el siguiente escaneo. F2 o Ctrl+K vuelven a la búsqueda. Un código duplicado exige elegir el artículo en pantalla. No se crean códigos de barras para artículos sin código.

Para actualizar, cerrar Cel-Fii y ejecutar el instalador 1.0.1. Se conservan el identificador de aplicación y la carpeta de configuración de la versión anterior.

## Impresora

Conectar la Global TP-POS58 por USB, instalar su controlador oficial y configurar el papel de 58 mm en Windows. Elegir la impresora en Configuración. La impresión abre el diálogo de Windows. El ticket se identifica como no fiscal.

## Facturación

Una venta no equivale a una factura. Se porta el flujo real de FiscalActivity de la APK: status, health, prepare, issue, document y attach. La emisión exige revisar receptor e importe y confirmar; pending recupera el mismo comprobante; authorized muestra número y CAE; external bloquea otra emisión. El PDF oficial del servidor se guarda o se abre en el lector de Windows para imprimir. La integración utiliza la misma clave de conexión de la APK. Una factura autorizada en producción todavía debe verificarse durante el uso real. No se emiten comprobantes fiscales durante las pruebas.

## Desarrollo

`npm install`, `npm test`, `npm start`, `npm run dist`.

Fuentes Electron: https://www.electronjs.org/docs/latest/tutorial/security
Instalador NSIS: https://www.electron.build/docs/nsis/
