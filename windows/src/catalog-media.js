(function (root) {
  'use strict';

  const slots = [
    ['photoUrl', 'photo', 'Foto URL', 'Foto'],
    ['photoUrl2', 'photo2', 'Foto URL_2', 'Foto_2'],
    ['photoUrl3', 'photo3', 'Foto URL_3', 'Foto_3']
  ];
  const driveIdPattern = /^[A-Za-z0-9_-]+$/;

  // AppSheet filenames need the authenticated photo service or an explicit
  // published URL. They are never relative to the installed desktop app.
  function safePhotoURL(value) {
    if (typeof value !== 'string' || !value.trim()) return '';
    let url;
    try { url = new URL(value.trim()); } catch (_) { return ''; }
    if (url.protocol !== 'https:' || url.username || url.password || !url.hostname) return '';

    if (url.hostname === 'drive.google.com') {
      const pathId = url.pathname.match(/^\/file\/d\/([A-Za-z0-9_-]+)(?:\/|$)/);
      const id = pathId ? pathId[1] : url.searchParams.get('id');
      if (!id || !driveIdPattern.test(id)) return '';
      return 'https://lh3.googleusercontent.com/d/' + id + '=w1200';
    }
    return url.href;
  }

  function slotURL(product, index) {
    if (!product || typeof product !== 'object') return '';
    for (const field of slots[index]) {
      const url = safePhotoURL(product[field]);
      if (url) return url;
    }
    return '';
  }

  function identity(value) {
    const url = new URL(value);
    if (url.hostname === 'lh3.googleusercontent.com') {
      const id = url.pathname.match(/^\/d\/([A-Za-z0-9_-]+)(?:=|$)/);
      if (id) return 'drive:' + id[1];
    }
    return url.href;
  }

  function photoURLs(product) {
    const urls = [], seen = new Set();
    for (let index = 0; index < slots.length; index++) {
      const url = slotURL(product, index);
      if (!url) continue;
      const key = identity(url);
      if (!seen.has(key)) { seen.add(key); urls.push(url); }
    }
    return urls;
  }

  function primaryPhotoURL(product) { return photoURLs(product)[0] || ''; }

  // Keep the connector's slot numbers even when one or more photos are missing.
  function photoSlots(product) { return slots.map((_, index) => slotURL(product, index)); }

  // The authenticated list remains authoritative for inventory and prices.
  // The public catalogue supplies only explicit image URLs for the same ID,
  // matching the Android app's PhotoMap/catalogPhotos behaviour.
  function mergeProductPhotos(products, publicProducts) {
    if (!Array.isArray(products)) throw new TypeError('La lista de productos es inválida');
    const published = new Map();
    for (const product of Array.isArray(publicProducts) ? publicProducts : []) {
      if (product && product.id !== undefined && product.id !== null && String(product.id)) {
        published.set(String(product.id), product);
      }
    }
    return products.map(product => {
      const result = { ...product };
      const publicProduct = published.get(String(product.id));
      for (let index = 0; index < slots.length; index++) {
        const url = slotURL(product, index) || slotURL(publicProduct, index);
        if (url) result[slots[index][0]] = url;
      }
      return result;
    });
  }

  const api = Object.freeze({ photoURLs, photoSlots, primaryPhotoURL, mergeProductPhotos });
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (typeof window === 'object' && root === window) window.CelFiiCatalogMedia = api;
})(typeof globalThis === 'object' ? globalThis : this);
