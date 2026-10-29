'use strict';
function runTests(api) {
  var passed = 0;
  function check(condition, message) {
    if (!condition) throw new Error(message);
    passed++;
  }
  function throws(fn, message) {
    var failed = false;
    try { fn(); } catch (error) { failed = error instanceof TypeError; }
    check(failed, message);
  }
  function parseCSV(csv) {
    var source = csv.replace(/^\ufeff/, ''), rows = [], row = [], value = '', quoted = false;
    for (var i = 0; i < source.length; i++) {
      var c = source[i];
      if (c === '"') {
        if (quoted && source[i + 1] === '"') { value += '"'; i++; }
        else quoted = !quoted;
      } else if (c === ';' && !quoted) { row.push(value); value = ''; }
      else if (c === '\r' && source[i + 1] === '\n' && !quoted) {
        row.push(value); rows.push(row); row = []; value = ''; i++;
      } else value += c;
    }
    if (quoted) throw new Error('CSV truncado');
    return rows;
  }
  var original = Object.freeze({
    number: '00017', client: 'María Pérez', phone: '02604545982',
    model: 'SAMSUNG Galaxy A13', service: 'Pantalla; vidrio "quebrado"\nNo enciende',
    status: 'Recibido', price: '58.000,50', deposit: '5.000', imei: '001234567890123',
    unlock: Object.freeze({ type: 'PIN', code: 'DONT_EXPORT_UNLOCK' }),
    token: 'DONT_EXPORT_TOKEN', fingerprint: 'DONT_EXPORT_INTERNAL',
    notes: 'DONT_EXPORT_NOTES', arbitrary: 'DONT_EXPORT_ARBITRARY'
  });
  var rows = Object.freeze([original]), result = api.build(rows, { scope: 'filtered', date: '2026-10-29' });
  var csv = parseCSV(result.content), header = csv[0], data = csv[1];
  check(result.rowCount === 1 && result.columnCount === 14, 'Cantidad y columnas correctas');
  check(csv.length === 2 && data.length === 14, 'Una fila completa sin división por saltos internos');
  check(data[header.indexOf('Cliente')] === 'María Pérez', 'Acentos conservados');
  check(data[header.indexOf('Número de orden')] === "'00017", 'Ceros del número conservados');
  check(data[header.indexOf('Teléfono')] === "'02604545982", 'Ceros del teléfono conservados');
  check(data[header.indexOf('IMEI')] === "'001234567890123", 'Ceros del IMEI conservados');
  check(data[header.indexOf('Falla declarada')] === original.service, 'Separador, comillas y saltos conservados');
  check(data[header.indexOf('Precio')] === '58.000,50', 'Importe argentino sin reinterpretar');
  check(result.content.charCodeAt(0) === 0xfeff, 'Marca UTF-8 para texto con acentos');
  check(result.fileName === 'CelFii-Taller-Historial-filtrado-2026-10-29.csv', 'Nombre del alcance filtrado');
  check(!/DONT_EXPORT/.test(result.content), 'Solo campos permitidos, sin desbloqueos ni datos internos');
  check(original.number === '00017' && original.phone === '02604545982', 'Fuentes inmutables conservadas');

  ['=SUM(1;2)', '+123', '-1+2', '@SUM(A1)', ' \t=2+2', '\u200b=2+2', '\r\n+3'].forEach(function (value) {
    var exported = parseCSV(api.build([{ client: value }]).content)[1][1];
    check(exported === "'" + value, 'Texto con fórmula potencial neutralizado');
  });
  var blank = parseCSV(api.build([{}]).content);
  check(blank.length === 2 && blank[1].every(function (value) { return value === ''; }), 'Orden histórica incompleta');
  check(parseCSV(api.build([]).content).length === 1, 'Exportación vacía con encabezado');
  check(api.build([], { date: '2024-02-29' }).fileName.indexOf('2024-02-29') >= 0, 'Fecha bisiesta');
  throws(function () { api.build(null); }, 'Entrada inválida');
  throws(function () { api.build([null]); }, 'Orden inválida no omitida silenciosamente');
  throws(function () { api.build([], { scope: 'whatever' }); }, 'Alcance inválido');
  throws(function () { api.build([], { date: '2026-02-29' }); }, 'Fecha inexistente');
  throws(function () { api.build([], { date: '../../file' }); }, 'Fecha no puede modificar ruta');

  var many = Array.from({ length: 5109 }, function (_, i) {
    return Object.freeze({ number: String(i + 1), client: 'Cliente ficticio ' + i, imei: '000000000000001' });
  });
  var all = api.build(many, { scope: 'all' });
  var allRows = parseCSV(all.content);
  check(all.rowCount === 5109 && allRows.length === 5110, 'Historial completo sin límite de filas');
  check(allRows[5109][0] === "'5109", 'Última fila incluida');
  check(all.fileName === 'CelFii-Taller-Historial-completo.csv', 'Nombre completo sin fecha UTC implícita');
  return passed;
}
if (typeof module === 'object' && module.exports) {
  module.exports = runTests;
  if (require.main === module) {
    console.log('history-export: ' + runTests(require('../shared/history-export.js')) + ' checks passed');
  }
}
