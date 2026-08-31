# Cel-Fii Ventas Remaster 1.0.0

Reinicio completo del sistema de ventas conservando la base de stock existente.

## Qué se protege

- Spreadsheet: `Cel-Fii Stock Real`
- ID: `1Fpow8nljHN21D2wBsi7r6IZf5WS1O7RQTUHzGGOXRZ4`
- `Articulos` es la fuente de stock y no se borra ni se reemplaza.
- Tampoco se modifican `Stock equipos`, `Clientes`, `Dolar`, `Categorías` ni `CatalogoWeb`.

La versión nueva crea y utiliza exclusivamente:

- `Ventas_Remaster`
- `Ventas_Detalle_Remaster`
- `Pedidos_Remaster`
- `Auditoria_Remaster`

## 1. Crear el Apps Script

1. Abrir la Google Sheet **Cel-Fii Stock Real**.
2. Elegir **Extensiones → Apps Script**.
3. Reemplazar todo `Code.gs` por `apps-script/Code.gs`.
4. Crear un archivo HTML llamado exactamente `index` y pegar `apps-script/index.html`.
5. Guardar y ejecutar `setupRemaster` desde el editor.
6. Autorizar el acceso solicitado. El resultado devuelve el token privado de conexión.
7. Ejecutar `resetRemasterData` una sola vez. Solo vacía las cuatro tablas Remaster.

## 2. Publicar la web

1. En Apps Script elegir **Implementar → Nueva implementación**.
2. Tipo: **Aplicación web**.
3. Ejecutar como: **Yo**.
4. Acceso: **Cualquier persona**.
5. Copiar la URL final terminada en `/exec`.
6. Abrir esa URL, elegir **Conexión** e ingresar el token devuelto por `setupRemaster`.

El token no debe publicarse en GitHub ni incluirse en el código Android. Se guarda localmente en cada dispositivo.

## 3. Compilar la APK

Copiar esta entrega a la raíz de un repositorio GitHub. La estructura debe conservar `android/` y `.github/`.

1. Abrir **Actions → Compilar APK Remaster → Run workflow**.
2. Pegar la URL `/exec` en `web_url`.
3. Ejecutar el workflow.
4. Descargar el artefacto `CelFii-Ventas-Remaster-1.0.0`.
5. Instalar `app-debug.apk` en el teléfono.
6. En el primer inicio, pulsar **Conexión** e ingresar el token.

## Seguridad e integridad

- El descuento de stock usa `LockService`, por lo que dos ventas simultáneas no pisan cantidades.
- Se valida la existencia y disponibilidad de cada producto antes de escribir una venta.
- `clientRequestId` evita ventas duplicadas por reintentos.
- La APK deshabilita copias de seguridad para no respaldar el token.
- Todo tráfico exige HTTPS.
- Cada operación importante queda registrada en `Auditoria_Remaster`.

## Recuperación

Si una publicación falla, no ejecutar funciones de borrado en `Articulos`. Se puede eliminar solamente las pestañas con sufijo `_Remaster` y volver a ejecutar `setupRemaster`.
