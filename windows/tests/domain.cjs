const {test}=require('node:test');const assert=require('node:assert/strict');const {salePayload,normalize,validateInvoice}=require('../src/domain.cjs');
const line={productId:'A',quantity:2,unitPrice:9500,originalPrice:9000};const fields={seller:'Cel-Fii',payments:[{method:'Efectivo',amount:10000},{method:'Transferencia',amount:9000}]};
test('pagos combinados conservan precio y cantidad',()=>{const p=salePayload([line],fields);assert.equal(p.lines[0].quantity,2);assert.equal(p.lines[0].unitPrice,9500);assert.equal(p.payments.length,2)});
test('rechaza diferencia de pagos',()=>assert.throws(()=>salePayload([line],{...fields,payments:[{method:'Efectivo',amount:18999}]}),/coincidir/));
test('rechaza cantidades negativas o fraccionarias',()=>{for(const q of [-1,0,1.5])assert.throws(()=>salePayload([{...line,quantity:q}],fields),/Cantidad/)});
test('rechaza duplicados y precios no válidos',()=>{assert.throws(()=>salePayload([line,line],fields),/duplicado/);for(const n of [NaN,-1,0])assert.throws(()=>salePayload([{...line,unitPrice:n}],fields))});
test('búsqueda ignora mayúsculas, acentos y formato USB-C',()=>assert.equal(normalize('CÁMARA USB-C'),normalize('camara tipo c')));
test('valida ticket contra detalle de venta',()=>{const s={id:'S',total:19000,lines:[{quantity:2,unitPrice:9500,total:19000}]};assert.equal(validateInvoice(s,'S'),s);assert.throws(()=>validateInvoice(s,'OTRA'));assert.throws(()=>validateInvoice({...s,total:19001},'S'));assert.throws(()=>validateInvoice({...s,lines:[{quantity:2,unitPrice:9500,total:9500}]},'S'))});
