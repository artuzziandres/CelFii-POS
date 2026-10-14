'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { photoURLs, photoSlots, primaryPhotoURL, mergeProductPhotos } = require('../src/catalog-media.js');

test('public Drive URLs from the real catalogue resolve to image URLs', () => {
  const id = '1Pc1rCIsRIdYsC9KuAXa1mH-EtkGJX5CG';
  for (const photo of [
    'https://drive.google.com/uc?export=view&id=' + id,
    'https://drive.google.com/file/d/' + id + '/view?usp=sharing',
    'https://drive.google.com/open?id=' + id
  ]) assert.equal(primaryPhotoURL({ photo }), 'https://lh3.googleusercontent.com/d/' + id + '=w1200');
});

test('AppSheet filenames and unsafe schemes are never invented or rendered as URLs', () => {
  for (const photo of [
    'Articulos_Images/4c2c8437.Foto.221406.jpg', './Articulos_Images/foto.jpg',
    'javascript:alert(1)', 'data:image/svg+xml,test', 'file:///C:/foto.jpg',
    'http://cdn.example.com/foto.jpg', 'https://user:password@example.com/foto.jpg',
    'https://drive.google.com/uc?id=bad%2Fidentifier', 'https://drive.google.com/file/d/'
  ]) assert.deepEqual(photoURLs({ photo }), [], photo);
});

test('three slots resolve independently, skip missing photos and deduplicate the same Drive image', () => {
  assert.deepEqual(photoURLs({
    photo: 'Articulos_Images/missing.jpg',
    photo2: 'https://drive.google.com/uc?id=abcDEF_123',
    photoUrl3: 'https://lh3.googleusercontent.com/d/abcDEF_123=w480?v=55'
  }), ['https://lh3.googleusercontent.com/d/abcDEF_123=w1200']);
  assert.deepEqual(photoURLs({
    photoUrl: 'https://cdn.example.com/one.jpg',
    photo2: 'https://cdn.example.com/two.jpg',
    photo3: 'https://cdn.example.com/three.jpg'
  }), ['https://cdn.example.com/one.jpg', 'https://cdn.example.com/two.jpg', 'https://cdn.example.com/three.jpg']);
});

test('ordinary HTTPS URLs with id queries remain untouched and invalid preferred URLs fall back', () => {
  const photo = 'https://cdn.example.com/photo?id=sku123&width=500';
  assert.equal(primaryPhotoURL({ photoUrl: 'javascript:bad', photo }), photo);
  assert.equal(primaryPhotoURL({ photoUrl: 'https://cdn.example.com/new.jpg', photo }), 'https://cdn.example.com/new.jpg');
  assert.deepEqual(photoURLs(null), []);
  assert.deepEqual(photoURLs({ photo: { url: photo } }), []);
});

test('photo slots retain missing positions and duplicates for authenticated fallback by slot number', () => {
  const url = 'https://cdn.example.com/two.jpg';
  assert.deepEqual(photoSlots({ photo: 'Articulos_Images/local.jpg', photo2: url }), ['', url, '']);
  assert.deepEqual(photoSlots({ photo: url, photo2: url, photo3: 'javascript:bad' }), [url, url, '']);
  assert.deepEqual(photoSlots(null), ['', '', '']);
});

test('merging published photos preserves prices, quantities, costs, original paths and every product', () => {
  const products = [
    { id: '4c2c8437', name: 'Cargador only V8 4.4A', photo: 'Articulos_Images/original.jpg', stock: 2, cashPrice: 9000, cost: 4200, extra: { note: 'private' } },
    { id: 'unpublished', name: 'Otro producto', stock: 0, cashPrice: 3000, costUsd: 3 }
  ];
  const before = structuredClone(products);
  const result = mergeProductPhotos(products, [
    { id: '4c2c8437', stock: 999, cashPrice: 1, cost: 0, photo: 'https://drive.google.com/uc?id=actual_photo' },
    { id: 'publicOnly', stock: 8, photo: 'https://cdn.example.com/other.jpg' }
  ]);
  assert.equal(result.length, 2);
  assert.deepEqual(products, before);
  assert.notEqual(result[0], products[0]);
  const { photoUrl, ...unchanged } = result[0];
  assert.deepEqual(unchanged, before[0]);
  assert.equal(photoUrl, 'https://lh3.googleusercontent.com/d/actual_photo=w1200');
  assert.deepEqual(result[1], before[1]);
});

test('authenticated explicit photo URLs win; public slots fill only unresolved slots', () => {
  const result = mergeProductPhotos([
    { id: 42, photoUrl: 'https://cdn.example.com/own.jpg', photo2: 'Articulos_Images/slot2.jpg', photo3: 'https://cdn.example.com/own3.jpg' }
  ], [{ id: '42', photo: 'https://cdn.example.com/public.jpg', photo2: 'https://cdn.example.com/public2.jpg', photo3: 'https://cdn.example.com/public3.jpg' }]);
  assert.deepEqual(photoURLs(result[0]), ['https://cdn.example.com/own.jpg', 'https://cdn.example.com/public2.jpg', 'https://cdn.example.com/own3.jpg']);
  assert.deepEqual(mergeProductPhotos([{ id: 'x' }], null), [{ id: 'x' }]);
});

test('the same utility is available to a browser renderer without Node privileges', () => {
  const context = { URL };
  context.window = context;
  vm.runInNewContext(fs.readFileSync(require.resolve('../src/catalog-media.js'), 'utf8'), context);
  assert.equal(context.CelFiiCatalogMedia.primaryPhotoURL({ photo: 'https://cdn.example.com/foto.jpg' }), 'https://cdn.example.com/foto.jpg');
  assert.equal(context.CelFiiCatalogMedia.photoSlots({ photo2: 'https://cdn.example.com/two.jpg' })[1], 'https://cdn.example.com/two.jpg');
});
