/* CelFii Taller - shared, local history CSV export. No network or storage. */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.CelFiiHistoryExport = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  var columns = [
    ['number', 'Número de orden'], ['client', 'Cliente'],
    ['phone', 'Teléfono'], ['model', 'Marca y modelo'],
    ['service', 'Falla declarada'], ['status', 'Estado'],
    ['inDate', 'Fecha de ingreso'], ['outDate', 'Fecha de entrega'],
    ['price', 'Precio'], ['deposit', 'Seña'], ['cost', 'Costo'],
    ['imei', 'IMEI'], ['accessories', 'Accesorios'],
    ['lastEditor', 'Último técnico']
  ];
  columns.forEach(function (column) { Object.freeze(column); });
  Object.freeze(columns);

  function text(value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'number') return Number.isFinite(value) ? String(value) : '';
    return typeof value === 'string' || typeof value === 'boolean' ? String(value) : '';
  }

  function cell(value, keepDigits) {
    var result = text(value);
    // CSV quoting does not stop spreadsheet formulas. Retain all original text
    // after a literal apostrophe; identifiers are also literals to keep zeros.
    var formula = /^[\s\u0000-\u001f\u007f-\u009f\u200b-\u200d\u2060\ufeff]*[=+\-@]/.test(result);
    var leadingControl = /^[\u0000-\u001f\u007f-\u009f]/.test(result);
    if (result && (keepDigits || formula || leadingControl)) result = "'" + result;
    return '"' + result.replace(/"/g, '""') + '"';
  }

  function dateSuffix(date) {
    if (date === undefined || date === '') return '';
    if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      throw new TypeError('La fecha debe tener formato AAAA-MM-DD.');
    }
    var parsed = new Date(date + 'T00:00:00.000Z');
    if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
      throw new TypeError('La fecha del archivo no es válida.');
    }
    return '-' + date;
  }

  function build(orders, options) {
    if (!Array.isArray(orders)) throw new TypeError('Se requiere una lista de órdenes.');
    options = options || {};
    var scope = options.scope === undefined ? 'all' : options.scope;
    if (scope !== 'all' && scope !== 'filtered') {
      throw new TypeError('Elegí historial completo o resultados filtrados.');
    }
    var suffix = dateSuffix(options.date);
    var lines = [columns.map(function (column) { return cell(column[1], false); }).join(';')];
    orders.forEach(function (order, index) {
      if (!order || typeof order !== 'object' || Array.isArray(order)) {
        throw new TypeError('La orden ' + (index + 1) + ' no tiene un formato válido.');
      }
      lines.push(columns.map(function (column) {
        var key = column[0];
        return cell(order[key], key === 'number' || key === 'phone' || key === 'imei');
      }).join(';'));
    });
    return {
      content: '\ufeff' + lines.join('\r\n') + '\r\n',
      mimeType: 'text/csv;charset=utf-8',
      fileName: 'CelFii-Taller-Historial-' + (scope === 'all' ? 'completo' : 'filtrado') + suffix + '.csv',
      rowCount: orders.length,
      columnCount: columns.length,
      scope: scope
    };
  }

  return Object.freeze({ build: build });
});
