const fs=require('node:fs'),path=require('node:path');
module.exports=async function(win,app){
  const output=path.join(__dirname,'../test-output');fs.mkdirSync(output,{recursive:true});
  const settings=await win.webContents.executeJavaScript('({settings:!!document.querySelector("#settingsForm"),bridge:typeof window.celfii.invoke,menu:[...document.querySelectorAll("nav button")].every(b=>!/^\\d/.test(b.textContent.trim()))})');
  if(!settings.settings||settings.bridge!=='function'||!settings.menu)throw Error('Configuración o menú inválido');
  fs.writeFileSync(path.join(output,'configuracion.png'),(await win.webContents.capturePage()).toPNG());
  const sample=JSON.parse(fs.readFileSync(path.join(__dirname,'fixtures/products.json'),'utf8'));
  const cache=[['4c2c8437','only.webp'],['ba83bbec','karsen.webp']].map(([id,file])=>[id+':1','data:image/webp;base64,'+fs.readFileSync(path.join(__dirname,'fixtures',file)).toString('base64')]);
  // Exercise authenticated-photo fallback deterministically for these two
  // records; the remaining images still use the public URLs.
  for(const p of sample)if(cache.some(([key])=>key===p.id+':1'))p.photo='';
  await win.webContents.executeJavaScript(`products=${JSON.stringify(sample)};for(const [key,value] of ${JSON.stringify(cache)})photoCache.set(key,value);fresh=true;page='sale';render();document.querySelector('#connection').textContent='Vista de prueba';`);
  for(let i=0;i<20;i++){await new Promise(r=>setTimeout(r,1000));if(await win.webContents.executeJavaScript("document.querySelectorAll('.product-photo.has-photo').length>=2"))break}
  const catalog=await win.webContents.executeJavaScript(`({photos:document.querySelectorAll('.product-photo.has-photo').length,search:document.querySelector('#search')===document.activeElement,cards:document.querySelectorAll('.product').length})`);
  if(catalog.photos<2||!catalog.search||catalog.cards!==sample.length)throw Error('Fotos o búsqueda no disponibles');
  const scanner=await win.webContents.executeJavaScript(`query='0001234567890';document.querySelector('#search').value=query;document.querySelector('#search').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));({quantity:cart[0]?.quantity,query,focused:document.activeElement===document.querySelector('#search')})`);
  if(scanner.quantity!==1||scanner.query!==''||!scanner.focused)throw Error('Falló el escaneo');
  for(let i=0;i<12;i++){await new Promise(r=>setTimeout(r,1000));if(await win.webContents.executeJavaScript("document.querySelectorAll('.product-photo.has-photo').length>=2"))break}
  const ready=await win.webContents.executeJavaScript("document.querySelectorAll('.product-photo.has-photo').length>=2");if(!ready)throw Error('Fotos ausentes después de escanear');
  fs.writeFileSync(path.join(output,'interfaz.png'),(await win.webContents.capturePage()).toPNG());
  await win.webContents.executeJavaScript("viewProduct(products[0].id)");
  for(let i=0;i<12;i++){await new Promise(r=>setTimeout(r,1000));if(await win.webContents.executeJavaScript("!!document.querySelector('#detailImage .has-photo')"))break}
  const detail=await win.webContents.executeJavaScript("!!document.querySelector('#detailImage .has-photo')");
  if(!detail)throw Error('Foto de detalle ausente');
  console.log('UI_SMOKE',JSON.stringify({settings,catalog,scanner,detail}));app.exit(0);
};
