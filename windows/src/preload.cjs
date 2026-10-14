const {contextBridge,ipcRenderer}=require('electron');
const channels=new Set(['settings:get','settings:save','products:get','cache:get','api','sale:create','sale:retry','photos:upload','printers:get','receipt:print','fiscal','fiscal:pdf','fiscal:attach']);
contextBridge.exposeInMainWorld('celfii',{invoke:(name,...args)=>{if(!channels.has(name))return Promise.reject(Error('Operación no permitida'));return ipcRenderer.invoke(name,...args)}});
