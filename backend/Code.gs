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
      stock: Math.floor(number_(cell_(source, headers, 'Stock Actual'))),
      photo: cell_(source, headers, 'Foto')
    });
  }
  return { ok: true, products: products };
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
      const stock = number_(productData[rowIndex][productHeaders['Stock Actual']]);
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
        total: lineTotal,
        newStock: stock - quantity
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
    const paymentSummary = body.payments.map(function(payment) {
      const installments = Number(payment.installments || 0);
      return payment.method + (installments ? ' (' + installments + ' cuotas)' : '')
        + ': $' + Number(payment.amount).toFixed(2);
    }).join(' + ');

    appendMappedRow_(salesSheet, salesHeaders, {
      idVenta: saleId,
      Fecha: now,
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

    const stockColumn = productHeaders['Stock Actual'] + 1;
    resolved.forEach(function(line) {
      productsSheet.getRange(line.rowIndex + 1, stockColumn).setValue(line.newStock);
    });
    SpreadsheetApp.flush();
    return { ok: true, saleId: saleId, total: total };
  } finally {
    lock.releaseLock();
  }
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
  return { ok: true, path: relativePath };
}

function ensureColumns_() {
  ensureHeaders_(sheet_(CONFIG.sheets.sales), [
    'idVenta', 'Fecha', 'idClientes', 'Vendedor', 'Total',
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
