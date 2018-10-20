/**
 * obj-loader.js provides functions for loading graphical models from the server.
 */

import * as Three from 'three'
import OBJLoader from 'three-obj-loader'
import MTLLoader from 'three-mtl-loader'

OBJLoader(Three)

const objLoader = new Three.OBJLoader()
const mtlLoader = new MTLLoader()
const noop = () => {}

export function loadObj (resource) {
  return new Promise((resolve, reject) => {
    objLoader.load(resource, resolve, noop, reject)
  })
}

export function loadMTL (resource) {
  return new Promise((resolve, reject) => {
    mtlLoader.load(resource, resolve, noop, reject)
  })
}

export async function load (resource) {
  const mtl = await loadMTL(`${resource}.mtl`)
  objLoader.setMaterials(mtl)
  const obj = await loadObj(`${resource}.obj`)
  return obj
}

export default {
  load,
  loadObj,
  loadMTL
}
