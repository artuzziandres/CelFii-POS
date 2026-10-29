# CelFii Taller: exportación local del historial

Este módulo agrega una exportación CSV por lista explícita de campos. Está preparado para compartir entre Android y Windows; todavía requiere integración en las aplicaciones actuales.

## Uso

Cargar `history-export.js` como script local y llamar:

```javascript
const file = CelFiiHistoryExport.build(orders, {
  scope: 'filtered', // 'all' para el historial completo
  date: '2026-10-29' // fecha local suministrada por la interfaz
});
// file.content, file.fileName, file.mimeType, file.rowCount
```

El llamador debe proporcionar todas las filas correspondientes al alcance elegido. La selección filtrada debe tomarse del conjunto filtrado completo, antes de paginar. Mostrar alcance y cantidad antes de exportar. Guardar mediante un puente nativo real o descarga compatible; informar éxito solo cuando el guardado resulte confirmado. No usar el exportador de PDF para escribir bytes CSV.

## Datos y compatibilidad

- Incluye orden, cliente, teléfono, modelo, falla, estado, fechas, precio, seña, costo, IMEI, accesorios y último técnico.
- No incluye notas internas, desbloqueos, tokens ni objetos internos. No usa red ni modifica órdenes.
- Conserva importes tal como están en la fuente, sin reinterpretar separadores argentinos.
- Usa punto y coma, comillas escapadas, CRLF y BOM. Los identificadores llevan una marca literal para conservar ceros; las fórmulas potenciales se convierten en texto. CSV no tiene un sistema de tipos: verificar también la importación real en la aplicación de planillas usada por el local.
- No hay límite artificial de filas ni interpretación implícita de una fecha UTC para nombrar el archivo.

## Validación

El 29/10/2026 pasaron 30 comprobaciones en V8: 5109 filas ficticias completas, campos históricos vacíos, acentos, comillas/saltos, ceros iniciales, importes argentinos, fórmulas potenciales, privacidad y entradas inválidas. Las fuentes de prueba son inmutables.

Para repetir en Node: `node tecnico/tests/history-export.cjs`. El script se suministra; esta sesión no pudo ejecutarlo con Node debido al bloqueo del entorno Windows. No se probó aún el guardado nativo, la importación en Excel ni la integración con APK/Windows.

## Integración pendiente

Tomar únicamente este módulo y sus pruebas; esta rama parte de un proyecto técnico anterior y NO reemplaza las fuentes vigentes de Library (APK 1.5.1 o posterior y proyecto Windows vigente). Mantener paquete, certificado, activación y almacenamiento. Después de integrar, ejecutar las comprobaciones del producto y reconstruir los entregables con versiones superiores.
