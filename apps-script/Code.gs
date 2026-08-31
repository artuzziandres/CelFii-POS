const CONFIG = Object.freeze({
  spreadsheetId: '1Fpow8nljHN21D2wBsi7r6IZf5WS1O7RQTUHzGGOXRZ4',
  stockSheet: 'Articulos',
  salesSheet: 'Ventas_Remaster',
  detailsSheet: 'Ventas_Detalle_Remaster',
  ordersSheet: 'Pedidos_Remaster',
  auditSheet: 'Auditoria_Remaster',
  timezone: 'America/Buenos_Aires',
  tokenProperty: 'CELFII_API_TOKEN'
});

const HEADERS = Object.freeze({
  sales: ['idVenta','Fecha','Vendedor','Total','Medio de pago','Estado','requestId','Cliente Nombre','Cliente Telefono','Garantia Dias','Mes'],
  details: ['idDetalle','idVenta','idArticulos','Producto','Cantidad','Precio Unitario','Total','Precio Original','Diferencia'],
  orders: ['Ticket','Fecha','Estado','Cliente Nombre','Cliente Telefono','Entrega','Direccion','Total','Lineas JSON','Comprobante','idVenta'],
  audit: ['Fecha','Accion','Entidad','Id','Detalle']
});

function doGet(e) {
  const action = String((e && e.parameter && e.parameter.action) || '').trim();
  if (!action) {
    const template = HtmlService.createTemplateFromFile('index');
    template.apiUrl = ScriptApp.getService().getUrl();
    return template.evaluate().setTitle('Cel-Fii Ventas')
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
  }
  try {
    authorize_(e.parameter.token);
    if (action === 'health') return json_({ok:true, version:'1.0.0'});
    if (action === 'products') return json_({ok:true, products:listProducts_()});
    if (action === 'sales') return json_({ok:true, sales:listSales_()});
    if (action === 'orders') return json_({ok:true, orders:listOrders_()});
    throw new Error('Accion GET desconocida: ' + action);
  } catch (err) { return failure_(err); }
}

function doPost(e) {
  try {
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    authorize_(body.token);
    const action = String(body.action || '');
    const handlers = {
      createSale: () => createSale_(body),
      createOrder: () => createOrder_(body),
      confirmOrder: () => confirmOrder_(body),
      cancelOrder: () => cancelOrder_(body),
      resetOperationalData: () => resetOperationalData_(body.confirmation)
    };
    if (!handlers[action]) throw new Error('Accion POST desconocida: ' + action);
    return json_(Object.assign({ok:true}, handlers[action]()));
  } catch (err) { return failure_(err); }
}

function setupRemaster() {
  const ss = db_();
  ensureSheet_(ss, CONFIG.salesSheet, HEADERS.sales);
  ensureSheet_(ss, CONFIG.detailsSheet, HEADERS.details);
  ensureSheet_(ss, CONFIG.ordersSheet, HEADERS.orders);
  ensureSheet_(ss, CONFIG.auditSheet, HEADERS.audit);
  const props = PropertiesService.getScriptProperties();
  if (!props.getProperty(CONFIG.tokenProperty)) props.setProperty(CONFIG.tokenProperty, Utilities.getUuid().replace(/-/g,''));
  return {webAppReady:true, token:props.getProperty(CONFIG.tokenProperty)};
}

function resetRemasterData() {
  return resetOperationalData_('BORRAR VENTAS Y PEDIDOS');
}

function resetOperationalData_(confirmation) {
  if (confirmation !== 'BORRAR VENTAS Y PEDIDOS') throw new Error('Confirmacion de reinicio invalida');
  const lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    const ss = db_();
    clearData_(ensureSheet_(ss, CONFIG.salesSheet, HEADERS.sales));
    clearData_(ensureSheet_(ss, CONFIG.detailsSheet, HEADERS.details));
    clearData_(ensureSheet_(ss, CONFIG.ordersSheet, HEADERS.orders));
    audit_('RESET','operaciones','all','Ventas y pedidos reiniciados. Articulos sin cambios.');
    return {reset:true};
  } finally { lock.releaseLock(); }
}

function listProducts_() {
  const sheet = requiredSheet_(CONFIG.stockSheet);
  const values = sheet.getDataRange().getDisplayValues();
  if (values.length < 2) return [];
  const h = headerMap_(values[0]);
  return values.slice(1).map((r, i) => ({
    row:i+2,
    id:cell_(r,h,'idArticulos'), name:cell_(r,h,'Nombre'), category:cell_(r,h,'Categoría'),
    cashPrice:number_(cell_(r,h,'Precio Efectivo')), cardPrice:number_(cell_(r,h,'Precio en 3 Cuotas')),
    stock:number_(cell_(r,h,'Stock Actual')), code:cell_(r,h,'Codigo'), backupCode:cell_(r,h,'Codigo_Backup'),
    photo:cell_(r,h,'Foto'), photoUrl:cell_(r,h,'Foto URL'), type:cell_(r,h,'Tipo') || 'Accesorio',
    description:cell_(r,h,'Descripcion '), minimumStock:number_(cell_(r,h,'Stock Minimo')),
    brand:cell_(r,h,'Marca'), models:cell_(r,h,'Modelos Compatibles'), color:cell_(r,h,'Color'),
    equipmentStatus:cell_(r,h,'Estado Equipo') || 'Disponible'
  })).filter(p => p.id && p.name);
}

function createSale_(body) {
  const requestId = required_(body.clientRequestId, 'requestId');
  const existing = findRow_(requiredSheet_(CONFIG.salesSheet), 'requestId', requestId);
  if (existing) return {saleId:existing.idVenta, duplicated:true};
  const lines = validateLines_(body.lines);
  const lock = LockService.getScriptLock(); lock.waitLock(30000);
  try {
    const stock = requiredSheet_(CONFIG.stockSheet);
    const data = stock.getDataRange().getDisplayValues();
    const h = headerMap_(data[0]);
    const byId = {};
    data.slice(1).forEach((r,i) => { const id=cell_(r,h,'idArticulos'); if(id) byId[id]={row:i+2,values:r}; });
    let total=0;
    const normalized = lines.map(line => {
      const found=byId[line.productId]; if(!found) throw new Error('Producto inexistente: '+line.productId);
      const available=number_(cell_(found.values,h,'Stock Actual'));
      if(available < line.quantity) throw new Error('Stock insuficiente para '+cell_(found.values,h,'Nombre'));
      const original=number_(cell_(found.values,h,'Precio Efectivo'));
      const price=line.unitPrice == null ? original : positive_(line.unitPrice,'precio');
      const subtotal=round_(price*line.quantity); total+=subtotal;
      return {id:line.productId,name:cell_(found.values,h,'Nombre'),quantity:line.quantity,price,original,subtotal,row:found.row,available};
    });
    normalized.forEach(line => stock.getRange(line.row, h['Stock Actual']+1).setValue(line.available-line.quantity));
    const saleId='V-'+Utilities.getUuid().slice(0,8).toUpperCase();
    const now=new Date();
    requiredSheet_(CONFIG.salesSheet).appendRow([saleId,now,String(body.seller||'Sin asignar'),round_(total),String(body.payment||'Efectivo'),'Confirmada',requestId,String(body.customerName||''),String(body.customerPhone||''),Number(body.warrantyDays||0),Utilities.formatDate(now,CONFIG.timezone,'yyyy-MM')]);
    const detail=requiredSheet_(CONFIG.detailsSheet);
    normalized.forEach(line => detail.appendRow(['D-'+Utilities.getUuid().slice(0,8).toUpperCase(),saleId,line.id,line.name,line.quantity,line.price,line.subtotal,line.original,round_(line.price-line.original)]));
    audit_('CREATE','venta',saleId,JSON.stringify({total:round_(total),items:normalized.length}));
    return {saleId,total:round_(total)};
  } finally { lock.releaseLock(); }
}

function createOrder_(body) {
  const lines=validateLines_(body.lines);
  const products={}; listProducts_().forEach(p=>products[p.id]=p);
  let total=0;
  const normalized=lines.map(l=>{ const p=products[l.productId]; if(!p) throw new Error('Producto inexistente'); const price=l.unitPrice==null?p.cashPrice:positive_(l.unitPrice,'precio'); total+=price*l.quantity; return {productId:p.id,name:p.name,quantity:l.quantity,unitPrice:price}; });
  const ticket='P-'+Utilities.getUuid().slice(0,8).toUpperCase();
  requiredSheet_(CONFIG.ordersSheet).appendRow([ticket,new Date(),'Pendiente',required_(body.customerName,'cliente'),required_(body.customerPhone,'telefono'),String(body.delivery||'Retiro'),String(body.address||''),round_(total),JSON.stringify(normalized),String(body.proof||''),'']);
  audit_('CREATE','pedido',ticket,JSON.stringify({total:round_(total)}));
  return {ticket,total:round_(total)};
}

function confirmOrder_(body) {
  const order=findRow_(requiredSheet_(CONFIG.ordersSheet),'Ticket',required_(body.ticket,'ticket'));
  if(!order) throw new Error('Pedido no encontrado');
  if(order.Estado==='Confirmado') return {saleId:order.idVenta, duplicated:true};
  if(order.Estado==='Cancelado') throw new Error('El pedido esta cancelado');
  const result=createSale_({clientRequestId:'ORDER-'+order.Ticket,seller:body.seller,payment:'Pedido web',customerName:order['Cliente Nombre'],customerPhone:order['Cliente Telefono'],lines:JSON.parse(order['Lineas JSON'])});
  updateRow_(requiredSheet_(CONFIG.ordersSheet),order.__row,{Estado:'Confirmado',idVenta:result.saleId});
  return {saleId:result.saleId};
}

function cancelOrder_(body) {
  const order=findRow_(requiredSheet_(CONFIG.ordersSheet),'Ticket',required_(body.ticket,'ticket'));
  if(!order) throw new Error('Pedido no encontrado');
  if(order.Estado==='Confirmado') throw new Error('Una venta confirmada no puede cancelarse aqui');
  updateRow_(requiredSheet_(CONFIG.ordersSheet),order.__row,{Estado:'Cancelado'});
  audit_('UPDATE','pedido',order.Ticket,'Cancelado'); return {ticket:order.Ticket};
}

function listSales_() { return objects_(requiredSheet_(CONFIG.salesSheet)).reverse(); }
function listOrders_() { return objects_(requiredSheet_(CONFIG.ordersSheet)).reverse(); }

function authorize_(candidate) {
  const expected=PropertiesService.getScriptProperties().getProperty(CONFIG.tokenProperty);
  if(!expected) throw new Error('Ejecuta setupRemaster primero');
  if(String(candidate||'')!==expected) throw new Error('No autorizado');
}
function db_(){ return SpreadsheetApp.openById(CONFIG.spreadsheetId); }
function requiredSheet_(name){ const s=db_().getSheetByName(name); if(!s) throw new Error('Falta hoja '+name+'. Ejecuta setupRemaster'); return s; }
function ensureSheet_(ss,name,headers){ let s=ss.getSheetByName(name); if(!s) s=ss.insertSheet(name); if(s.getLastRow()===0) s.getRange(1,1,1,headers.length).setValues([headers]); s.setFrozenRows(1); return s; }
function clearData_(s){ if(s.getLastRow()>1) s.getRange(2,1,s.getLastRow()-1,s.getMaxColumns()).clearContent(); }
function headerMap_(row){ const m={}; row.forEach((v,i)=>m[String(v).trim()]=i); return m; }
function cell_(row,h,name){ const i=h[name]; return i==null?'':String(row[i]||'').trim(); }
function number_(v){ if(typeof v==='number') return v; let s=String(v||'').replace(/[$\s]/g,''); if(s.includes(',')) s=s.replace(/\./g,'').replace(',','.'); const n=Number(s); return Number.isFinite(n)?n:0; }
function positive_(v,name){ const n=Number(v); if(!Number.isFinite(n)||n<0) throw new Error(name+' invalido'); return n; }
function required_(v,name){ const s=String(v||'').trim(); if(!s) throw new Error('Falta '+name); return s; }
function round_(n){ return Math.round((Number(n)+Number.EPSILON)*100)/100; }
function validateLines_(lines){ if(!Array.isArray(lines)||!lines.length) throw new Error('La venta no tiene productos'); return lines.map(l=>({productId:required_(l.productId,'producto'),quantity:Math.max(1,Math.floor(positive_(l.quantity,'cantidad'))),unitPrice:l.unitPrice==null?null:positive_(l.unitPrice,'precio')})); }
function objects_(s){ const v=s.getDataRange().getValues(); if(v.length<2)return[]; const h=v[0].map(String); return v.slice(1).filter(r=>r.some(x=>x!=='' )).map(r=>Object.fromEntries(h.map((k,i)=>[k,r[i] instanceof Date?Utilities.formatDate(r[i],CONFIG.timezone,'yyyy-MM-dd HH:mm:ss'):r[i]]))); }
function findRow_(s,key,value){ return objects_(s).map((o,i)=>Object.assign({__row:i+2},o)).find(o=>String(o[key])===String(value)); }
function updateRow_(s,row,changes){ const h=headerMap_(s.getRange(1,1,1,s.getLastColumn()).getValues()[0]); Object.keys(changes).forEach(k=>{ if(h[k]==null) throw new Error('Columna inexistente '+k); s.getRange(row,h[k]+1).setValue(changes[k]); }); }
function audit_(action,entity,id,detail){ ensureSheet_(db_(),CONFIG.auditSheet,HEADERS.audit).appendRow([new Date(),action,entity,id,detail]); }
function json_(data){ return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON); }
function failure_(err){ console.error(err && err.stack || err); return json_({ok:false,error:String(err && err.message || err)}); }
