/**
 * Cel-Fii POS · Conector seguro Google Sheets / Drive
 * Planilla: Cel-Fii Stock Real
 */
const CONFIG = Object.freeze({
  spreadsheetId: '1Fpow8nljHN21D2wBsi7r6IZf5WS1O7RQTUHzGGOXRZ4',
  photoFolderId: '1i9itjBM-CdZTdaamhwF1tD_Jgv6FsuqJ',
  sheets: {
    products: 'Articulos',
    sales: 'Ventas',
    details: 'Ventas_Detalle',
    customers: 'Clientes',
    sellers: 'Vendedores'
  }
});

function doGet(e) {
  try {
    validateToken_(e.parameter.token);
    const action = String(e.parameter.action || '');
    if (action === 'products') return json_(getProducts_(e.parameter.q || ''));
    if (action === 'productPhoto') return json_(getProductPhoto_(e.parameter.productId || ''));
    if (action === 'sales') return json_(getSales_(e.parameter.month || ''));
    if (action === 'health') return json_({ ok: true, service: 'Cel-Fii POS' });
    throw new Error('Acción no válida');
  } catch (error) {
    return json_({ ok: false, error: error.message });
  }
}

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents || '{}');
    validateToken_(body.token);
    if (body.action === 'createSale') return json_(createSale_(body));
    if (body.action === 'createProduct') return json_(createProduct_(body));
    if (body.action === 'updateProduct') return json_(updateProduct_(body));
    if (body.action === 'uploadProductPhoto') return json_(uploadProductPhoto_(body));
    throw new Error('Acción no válida');
  } catch (error) {
    return json_({ ok: false, error: error.message });
  }
}

function setupCelFiiPos() {
  const token = Utilities.getUuid() + Utilities.getUuid();
  PropertiesService.getScriptProperties().setProperty('API_TOKEN', token);
  ensureColumns_();
  console.log('CELFII_API_TOKEN=' + token);
  return token;
}

function getProducts_(query) {
  const sheet = sheet_(CONFIG.sheets.products);
  const values = sheet.getDataRange().getDisplayValues();
  if (values.length < 2) return { ok: true, products: [] };
  const headers = headerMap_(values[0]);
  const needle = normalize_(query);
  const products = [];

  for (let row = 1; row < values.length; row++) {
    const source = values[row];
    const name = cell_(source, headers, 'Nombre');
    const id = cell_(source, headers, 'idArticulos');
    if (!name || !id) continue;
    const searchable = normalize_([
      name,
      cell_(source, headers, 'Categoría'),
      cell_(source, headers, 'Codigo'),
      cell_(source, headers, 'Codigo_Backup')
    ].join(' '));
    if (needle && searchable.indexOf(needle) === -1) continue;
    products.push({
      id: id,
      name: name,
      category: cell_(source, headers, 'Categoría'),
      cashPrice: number_(cell_(source, headers, 'Precio Efectivo')),
      cardPrice: number_(cell_(source, headers, 'Precio en 3 Cuotas')),
      stock: Math.floor(number_(cell_(source, headers,
        headers['Stock Actual 2'] !== undefined ? 'Stock Actual 2' : 'Stock Actual'))),
      photo: cell_(source, headers, 'Foto'),
      code: cell_(source, headers, 'Codigo'),
      backupCode: cell_(source, headers, 'Codigo_Backup'),
      type: cell_(source, headers, 'Tipo'),
      description: cell_(source, headers, 'Descripcion '),
      minimumStock: Math.floor(number_(cell_(source, headers, 'Stock Minimo'))),
      costUsd: number_(cell_(source, headers, 'Costo en Dolares'))
    });
  }
  return { ok: true, products: products };
}

function getSales_(requestedMonth) {
  ensureColumns_();
  const salesSheet = sheet_(CONFIG.sheets.sales);
  const detailsSheet = sheet_(CONFIG.sheets.details);
  const productsSheet = sheet_(CONFIG.sheets.products);
  const salesData = salesSheet.getDataRange().getValues();
  const detailData = detailsSheet.getDataRange().getValues();
  const productData = productsSheet.getDataRange().getValues();
  if (salesData.length < 2) return { ok: true, sales: [] };

  const salesHeaders = headerMap_(salesData[0]);
  const detailHeaders = detailData.length ? headerMap_(detailData[0]) : {};
  const productHeaders = productData.length ? headerMap_(productData[0]) : {};
  const productNames = {};
  const detailsBySale = {};

  for (let row = 1; row < productData.length; row++) {
    const id = cell_(productData[row], productHeaders, 'idArticulos');
    if (id) productNames[id] = cell_(productData[row], productHeaders, 'Nombre');
  }

  for (let row = 1; row < detailData.length; row++) {
    const source = detailData[row];
    const saleId = cell_(source, detailHeaders, 'idVenta');
    if (!saleId) continue;
    const productId = cell_(source, detailHeaders, 'idArticulos');
    if (!detailsBySale[saleId]) detailsBySale[saleId] = [];
    detailsBySale[saleId].push({
      productId: productId,
      name: productNames[productId] || productId,
      quantity: Math.floor(number_(cell_(source, detailHeaders, 'Cantidad'))),
      unitPrice: number_(cell_(source, detailHeaders, 'Precio Unitario')),
      total: number_(cell_(source, detailHeaders, 'Total'))
    });
  }

  const timezone = SpreadsheetApp.openById(CONFIG.spreadsheetId)
    .getSpreadsheetTimeZone() || Session.getScriptTimeZone();
  const monthFilter = String(requestedMonth || '').trim();
  const sales = [];

  for (let row = 1; row < salesData.length; row++) {
    const source = salesData[row];
    const saleId = cell_(source, salesHeaders, 'idVenta');
    if (!saleId) continue;
    const rawDate = source[salesHeaders['Fecha']];
    const validDate = rawDate instanceof Date && !isNaN(rawDate.getTime());
    const month = cell_(source, salesHeaders, 'Mes')
      || (validDate ? Utilities.formatDate(rawDate, timezone, 'yyyy-MM') : 'Sin fecha');
    if (monthFilter && month !== monthFilter) continue;
    sales.push({
      id: saleId,
      timestamp: validDate ? rawDate.getTime() : 0,
      date: validDate ? Utilities.formatDate(rawDate, timezone, 'dd/MM/yyyy HH:mm')
        : String(rawDate || ''),
      month: month,
      seller: cell_(source, salesHeaders, 'Vendedor'),
      total: number_(cell_(source, salesHeaders, 'Total')),
      payment: cell_(source, salesHeaders, 'Medios de pago'),
      status: cell_(source, salesHeaders, 'Estado'),
      details: detailsBySale[saleId] || []
    });
  }

  sales.sort(function(a, b) { return b.timestamp - a.timestamp; });
  return { ok: true, sales: sales };
}

function createSale_(body) {
  if (!Array.isArray(body.lines) || body.lines.length === 0) {
    throw new Error('La venta no contiene productos');
  }
  if (!Array.isArray(body.payments) || body.payments.length === 0) {
    throw new Error('La venta no contiene pagos');
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    ensureColumns_();
    const salesSheet = sheet_(CONFIG.sheets.sales);
    const detailsSheet = sheet_(CONFIG.sheets.details);
    const productsSheet = sheet_(CONFIG.sheets.products);
    const salesData = salesSheet.getDataRange().getValues();
    const salesHeaders = headerMap_(salesData[0]);

    const existing = findRowByValue_(
      salesData, salesHeaders['requestId'], String(body.clientRequestId || '')
    );
    if (existing > 0) {
      return {
        ok: true,
        saleId: String(salesData[existing][salesHeaders['idVenta']]),
        duplicated: true
      };
    }

    const productData = productsSheet.getDataRange().getValues();
    const productHeaders = headerMap_(productData[0]);
    const resolved = [];
    let total = 0;

    body.lines.forEach(function(line) {
      const rowIndex = findRowByValue_(
        productData, productHeaders['idArticulos'], String(line.productId)
      );
      if (rowIndex < 1) throw new Error('Artículo inexistente: ' + line.productId);
      const quantity = Math.floor(Number(line.quantity));
      const unitPrice = Number(line.unitPrice);
      const stockHeader = productHeaders['Stock Actual 2'] !== undefined
        ? productHeaders['Stock Actual 2'] : productHeaders['Stock Actual'];
      const stock = number_(productData[rowIndex][stockHeader]);
      if (!quantity || quantity < 1) throw new Error('Cantidad inválida');
      if (stock < quantity) {
        throw new Error(
          'Stock insuficiente de ' + productData[rowIndex][productHeaders['Nombre']]
          + '. Disponible: ' + stock
        );
      }
      const lineTotal = round2_(quantity * unitPrice);
      total += lineTotal;
      resolved.push({
        rowIndex: rowIndex,
        productId: String(line.productId),
        quantity: quantity,
        unitPrice: unitPrice,
        total: lineTotal
      });
    });

    total = round2_(total);
    const paid = round2_(body.payments.reduce(function(sum, payment) {
      return sum + Number(payment.amount || 0);
    }, 0));
    if (Math.abs(total - paid) > 0.01) {
      throw new Error('Los medios de pago no coinciden con el total');
    }

    const saleId = Utilities.getUuid().split('-')[0].toUpperCase();
    const now = new Date();
    const timezone = SpreadsheetApp.openById(CONFIG.spreadsheetId)
      .getSpreadsheetTimeZone() || Session.getScriptTimeZone();
    const saleMonth = Utilities.formatDate(now, timezone, 'yyyy-MM');
    const paymentSummary = body.payments.map(function(payment) {
      const installments = Number(payment.installments || 0);
      return payment.method + (installments ? ' (' + installments + ' cuotas)' : '')
        + ': $' + Number(payment.amount).toFixed(2);
    }).join(' + ');

    appendMappedRow_(salesSheet, salesHeaders, {
      idVenta: saleId,
      Fecha: now,
      Mes: saleMonth,
      idClientes: String(body.customerId || ''),
      Vendedor: String(body.seller || ''),
      Total: total,
      'Medios de pago': paymentSummary,
      'Pagos JSON': JSON.stringify(body.payments),
      Estado: 'Confirmada',
      requestId: String(body.clientRequestId || '')
    });

    const detailHeaders = headerMap_(detailsSheet.getRange(
      1, 1, 1, detailsSheet.getLastColumn()
    ).getValues()[0]);
    const detailRows = resolved.map(function(line) {
      return mappedRow_(detailsSheet.getLastColumn(), detailHeaders, {
        idDetalle: Utilities.getUuid().split('-')[0].toUpperCase(),
        idVenta: saleId,
        idArticulos: line.productId,
        Cantidad: line.quantity,
        'Precio Unitario': line.unitPrice,
        Total: line.total
      });
    });
    detailsSheet.getRange(
      detailsSheet.getLastRow() + 1, 1, detailRows.length, detailsSheet.getLastColumn()
    ).setValues(detailRows);

    SpreadsheetApp.flush();
    return { ok: true, saleId: saleId, total: total };
  } finally {
    lock.releaseLock();
  }
}

function createProduct_(body) {
  const product = body.product || {};
  if (!String(product.name || '').trim()) throw new Error('Falta el nombre');
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const sheet = sheet_(CONFIG.sheets.products);
    const headers = headerMap_(sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0]);
    const id = Utilities.getUuid().replace(/-/g, '').substring(0, 8);
    const row = sheet.getLastRow() + 1;
    if (row > sheet.getMaxRows()) sheet.insertRowsAfter(sheet.getMaxRows(), 1);
    prepareNewProductRow_(sheet, row);
    writeProductFields_(sheet, headers, row, product, true, id);
    SpreadsheetApp.flush();
    return { ok: true, productId: id };
  } finally {
    lock.releaseLock();
  }
}

/**
 * Conserva en cada alta las validaciones, el formato y las fórmulas de la tabla
 * (especialmente Stock Actual 2). Así la fila también funciona en AppSheet y web.
 */
function prepareNewProductRow_(sheet, row) {
  if (row <= 2) return;
  const columns = sheet.getLastColumn();
  const source = sheet.getRange(row - 1, 1, 1, columns);
  const target = sheet.getRange(row, 1, 1, columns);
  source.copyTo(target, SpreadsheetApp.CopyPasteType.PASTE_FORMAT, false);
  source.copyTo(target, SpreadsheetApp.CopyPasteType.PASTE_DATA_VALIDATION, false);
  const formulas = source.getFormulasR1C1()[0];
  formulas.forEach(function(formula, column) {
    if (formula) target.getCell(1, column + 1).setFormulaR1C1(formula);
  });
}

function updateProduct_(body) {
  const product = body.product || {};
  const id = String(product.id || '');
  if (!id) throw new Error('Falta el artículo');
  if (!String(product.name || '').trim()) throw new Error('Falta el nombre');
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const sheet = sheet_(CONFIG.sheets.products);
    const values = sheet.getDataRange().getValues();
    const headers = headerMap_(values[0]);
    const index = findRowByValue_(values, headers['idArticulos'], id);
    if (index < 1) throw new Error('Artículo inexistente');
    writeProductFields_(sheet, headers, index + 1, product, false, id);
    SpreadsheetApp.flush();
    return { ok: true, productId: id };
  } finally {
    lock.releaseLock();
  }
}

function writeProductFields_(sheet, headers, row, product, creating, id) {
  const values = {
    Nombre: String(product.name || '').trim(),
    'Categoría': String(product.category || '').trim(),
    'Precio Efectivo': number_(product.cashPrice),
    'Precio en 3 Cuotas': number_(product.cardPrice),
    idArticulos: id,
    Codigo: String(product.code || '').trim(),
    Tipo: String(product.type || 'Accesorio').trim(),
    Fecha: creating ? new Date() : undefined,
    'Costo en Dolares': number_(product.costUsd),
    'Descripcion ': String(product.description || '').trim(),
    'Stock Minimo': Math.max(0, Math.floor(number_(product.minimumStock))),
    Codigo_Backup: String(product.backupCode || '').trim()
  };
  if (creating) {
    values['Stock Inicial'] = Math.max(0, Math.floor(number_(product.initialStock)));
    values['Stock Actual'] = values['Stock Inicial'];
  }
  Object.keys(values).forEach(function(name) {
    if (values[name] !== undefined && headers[name] !== undefined) {
      sheet.getRange(row, headers[name] + 1).setValue(values[name]);
    }
  });
}

function uploadProductPhoto_(body) {
  if (!body.productId || !body.base64 || !body.mimeType) {
    throw new Error('Faltan datos de la imagen');
  }
  const bytes = Utilities.base64Decode(body.base64);
  const extension = body.mimeType === 'image/png' ? '.png' : '.jpg';
  const fileName = body.productId + '.Foto.' + Date.now() + extension;
  const blob = Utilities.newBlob(bytes, body.mimeType, fileName);
  DriveApp.getFolderById(CONFIG.photoFolderId).createFile(blob);

  const sheet = sheet_(CONFIG.sheets.products);
  const values = sheet.getDataRange().getValues();
  const headers = headerMap_(values[0]);
  const rowIndex = findRowByValue_(values, headers['idArticulos'], String(body.productId));
  if (rowIndex < 1) throw new Error('Artículo inexistente');
  const relativePath = 'Articulos_Images/' + fileName;
  sheet.getRange(rowIndex + 1, headers['Foto'] + 1).setValue(relativePath);
  SpreadsheetApp.flush();
  return { ok: true, path: relativePath };
}

function getProductPhoto_(productId) {
  if (!productId) throw new Error('Falta el artículo');
  const sheet = sheet_(CONFIG.sheets.products);
  const values = sheet.getDataRange().getValues();
  const headers = headerMap_(values[0]);
  const rowIndex = findRowByValue_(values, headers['idArticulos'], String(productId));
  if (rowIndex < 1) throw new Error('Artículo inexistente');
  const path = String(values[rowIndex][headers['Foto']] || '');
  const fileName = path.split('/').pop();
  if (!fileName) throw new Error('El artículo no tiene foto');
  const files = DriveApp.getFolderById(CONFIG.photoFolderId).getFilesByName(fileName);
  if (!files.hasNext()) throw new Error('No se encontró la foto');
  const blob = files.next().getBlob();
  return {
    ok: true,
    mimeType: blob.getContentType(),
    base64: Utilities.base64Encode(blob.getBytes())
  };
}

function ensureColumns_() {
  ensureHeaders_(sheet_(CONFIG.sheets.sales), [
    'idVenta', 'Fecha', 'Mes', 'idClientes', 'Vendedor', 'Total',
    'Medios de pago', 'Pagos JSON', 'Estado', 'requestId'
  ]);
  ensureHeaders_(sheet_(CONFIG.sheets.details), [
    'idDetalle', 'idVenta', 'idArticulos', 'Cantidad', 'Precio Unitario', 'Total'
  ]);
}

function ensureHeaders_(sheet, required) {
  const lastColumn = Math.max(sheet.getLastColumn(), 1);
  const headers = sheet.getRange(1, 1, 1, lastColumn).getValues()[0];
  required.forEach(function(header) {
    if (headers.indexOf(header) === -1) headers.push(header);
  });
  if (sheet.getMaxColumns() < headers.length) {
    sheet.insertColumnsAfter(
      sheet.getMaxColumns(),
      headers.length - sheet.getMaxColumns()
    );
  }
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
}

function appendMappedRow_(sheet, headers, values) {
  sheet.appendRow(mappedRow_(sheet.getLastColumn(), headers, values));
}

function mappedRow_(length, headers, values) {
  const row = new Array(length).fill('');
  Object.keys(values).forEach(function(key) {
    if (headers[key] !== undefined) row[headers[key]] = values[key];
  });
  return row;
}

function sheet_(name) {
  const sheet = SpreadsheetApp.openById(CONFIG.spreadsheetId).getSheetByName(name);
  if (!sheet) throw new Error('No existe la hoja ' + name);
  return sheet;
}

function headerMap_(headers) {
  return headers.reduce(function(map, header, index) {
    map[String(header).trim()] = index;
    return map;
  }, {});
}

function cell_(row, headers, name) {
  const index = headers[name];
  return index === undefined ? '' : String(row[index] || '').trim();
}

function findRowByValue_(rows, column, value) {
  if (column === undefined || !value) return -1;
  for (let i = 1; i < rows.length; i++) {
    if (String(rows[i][column]) === value) return i;
  }
  return -1;
}

function normalize_(value) {
  return String(value || '').normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

function number_(value) {
  if (typeof value === 'number') return value;
  let text = String(value || '').replace(/[^\d,.-]/g, '');
  if (text.indexOf(',') >= 0) text = text.replace(/\./g, '').replace(',', '.');
  const result = Number(text);
  return isNaN(result) ? 0 : result;
}

function round2_(value) {
  return Math.round((Number(value) + Number.EPSILON) * 100) / 100;
}

function validateToken_(provided) {
  const expected = PropertiesService.getScriptProperties().getProperty('API_TOKEN');
  if (!expected || provided !== expected) throw new Error('Acceso no autorizado');
}

function json_(value) {
  return ContentService.createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}
