# Cel-Fii POS Android

Primera versión del punto de venta propio de Cel-Fii Tecnología.

## Incluido

- Venta con múltiples productos.
- Lectura de catálogo y stock desde `Cel-Fii Stock Real`.
- Pagos: efectivo, transferencia, débito, crédito y pagos combinados.
- Cantidad de cuotas para crédito.
- Escritura atómica en `Ventas` y `Ventas_Detalle`.
- Descuento de stock con bloqueo para varios celulares simultáneos.
- Prevención de ventas duplicadas mediante `clientRequestId`.
- Impresión ESC/POS de 58 mm por Bluetooth clásico.
- Subida de fotos compatible con `Articulos_Images`.
- Interfaz negro, verde lima y paneles de acuerdo con www.cel-fii.com.

## Configuración del servidor

1. Crear un proyecto en Google Apps Script desde la cuenta propietaria de la planilla.
2. Copiar `backend/Code.gs` y `backend/appsscript.json`.
3. Ejecutar una vez `setupCelFiiPos()` y autorizar Sheets/Drive.
4. Guardar el token mostrado en el registro.
5. Implementar como **Aplicación web**:
   - Ejecutar como: usuario que implementa.
   - Acceso: cualquier usuario.
6. Copiar la URL terminada en `/exec`.

El servidor usa:

- Planilla: `1Fpow8nljHN21D2wBsi7r6IZf5WS1O7RQTUHzGGOXRZ4`
- Fotos: `Articulos_Images` (`1i9itjBM-CdZTdaamhwF1tD_Jgv6FsuqJ`)

## Configuración Android

Agregar al archivo `~/.gradle/gradle.properties`:

```properties
CELFII_API_URL=https://script.google.com/macros/s/IMPLEMENTACION/exec
CELFII_API_TOKEN=TOKEN_GENERADO
```

Abrir el proyecto con Android Studio, sincronizar y ejecutar en Android 8 o superior.

Antes de imprimir, vincular la impresora Global TP-POS58 desde los ajustes Bluetooth
del teléfono. La app solicita permiso de dispositivos cercanos y permite elegir una
impresora vinculada al finalizar la venta.

## Próximos módulos

- Alta/edición de productos y cámara.
- Clientes y vendedores.
- Historial y reimpresión.
- Ingresos, devoluciones y caja.
- Servicio técnico.
- Informes y control de ganancias.
