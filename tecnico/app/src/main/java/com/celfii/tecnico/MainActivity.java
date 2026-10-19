package com.celfii.tecnico;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.print.PrintManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class MainActivity extends Activity {
 private static final String ORIGIN="https://appassets.androidplatform.net/";
 private static final String KEY_ALIAS="celfii.tecnico.connection.v1";
 private WebView web;
 private final ExecutorService jobs=Executors.newSingleThreadExecutor();
 private SharedPreferences prefs;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);prefs=getSharedPreferences("connection",MODE_PRIVATE);
  getWindow().setStatusBarColor(Color.rgb(9,12,10));getWindow().setNavigationBarColor(Color.rgb(9,12,10));
  web=new WebView(this);web.setBackgroundColor(Color.rgb(9,12,10));setContentView(web);
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);
  web.addJavascriptInterface(new Bridge(),"CelFiiNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
    String url=r.getUrl().toString();if(!url.startsWith(ORIGIN))return denied();
    String path=r.getUrl().getPath();String name="/pin.png".equals(path)?"pin.png":"/".equals(path)||"/index.html".equals(path)?"index.html":null;
    if(name==null)return denied();try{return new WebResourceResponse(name.endsWith("png")?"image/png":"text/html","UTF-8",getAssets().open(name));}catch(IOException e){return denied();}
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
    Uri uri=r.getUrl();if(uri.toString().startsWith(ORIGIN))return false;
    if(r.isForMainFrame()&&"https".equals(uri.getScheme())&&("wa.me".equals(uri.getHost())||"api.whatsapp.com".equals(uri.getHost()))){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}}return true;
   }
  });web.loadUrl(ORIGIN+"index.html");
 }
 private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
 private SecretKey key() throws Exception {
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias(KEY_ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();}
  return ((KeyStore.SecretKeyEntry)store.getEntry(KEY_ALIAS,null)).getSecretKey();
 }
 private String encrypt(String value)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
 private String decrypt(String value)throws Exception{String[] parts=value.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
 private boolean endpoint(String value){return value!=null&&value.matches("https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]{20,}/exec");}
 private void respond(String id,JSONObject result){runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onNativeResponse("+JSONObject.quote(id)+","+result.toString()+")",null);});}
 private JSONObject error(String value){JSONObject o=new JSONObject();try{o.put("ok",false).put("error",value);}catch(Exception ignored){}return o;}
 private JSONObject post(String endpoint,JSONObject payload)throws Exception{
  URL url=new URL(endpoint);byte[] body=payload.toString().getBytes(StandardCharsets.UTF_8);boolean send=true;
  for(int n=0;n<4;n++){
   if(!"https".equals(url.getProtocol())||!("script.google.com".equals(url.getHost())||"script.googleusercontent.com".equals(url.getHost())))throw new IOException("Destino de conexión inválido");
   HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");
   if(send){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body);}}
   try{int status=c.getResponseCode();if(status==301||status==302||status==303){String location=c.getHeaderField("Location");if(location==null)throw new IOException();url=new URL(url,location);send=false;continue;}
    if(status!=200)throw new IOException("El servidor respondió "+status);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int read;while((read=in.read(buf))!=-1){out.write(buf,0,read);if(out.size()>12*1024*1024)throw new IOException("Respuesta demasiado grande");}return new JSONObject(out.toString("UTF-8"));}
   }finally{c.disconnect();}
  }throw new IOException("No se pudo completar la conexión");
 }
 public final class Bridge {
  @JavascriptInterface public boolean isConfigured(){return endpoint(prefs.getString("url",""))&&prefs.contains("token");}
  @JavascriptInterface public String getEndpoint(){return prefs.getString("url","");}
  @JavascriptInterface public synchronized String configure(String url,String token){
   if(!endpoint(url)||token==null||!token.matches("[A-Za-z0-9_-]{40,200}"))return error("Revisá la URL y la clave de activación").toString();
   try{prefs.edit().putString("url",url).putString("token",encrypt(token)).commit();return "{\"ok\":true}";}catch(Exception e){return error("No se pudo guardar la conexión protegida").toString();}
  }
  @JavascriptInterface public void request(String id,String action,String argument){
   if(id==null||id.length()>80||!("getData".equals(action)||"saveOrder".equals(action))||argument==null||argument.length()>100000)return;
   jobs.submit(()->{try{if(!isConfigured()){respond(id,error("Activá la conexión con tu planilla"));return;}JSONObject body=new JSONObject().put("token",decrypt(prefs.getString("token",""))).put("action",action).put("args",new JSONObject(argument));respond(id,post(prefs.getString("url",""),body));}catch(Exception e){respond(id,error("No se pudo confirmar la operación. Revisá la conexión y actualizá antes de repetir un guardado."));}});
  }
  @JavascriptInterface public void printReceipt(){runOnUiThread(()->{if(web!=null)((PrintManager)getSystemService(PRINT_SERVICE)).print("CelFii-Recepcion",web.createPrintDocumentAdapter("CelFii-Recepcion"),null);});}
 }
 @Override public void onBackPressed(){web.evaluateJavascript("(()=>{const d=document.querySelector('dialog[open]');if(d){d.close();return true;}return false;})()",value->{if(!"true".equals(value))finish();});}
 @Override protected void onDestroy(){jobs.shutdownNow();if(web!=null){web.removeJavascriptInterface("CelFiiNative");web.destroy();web=null;}super.onDestroy();}
}
