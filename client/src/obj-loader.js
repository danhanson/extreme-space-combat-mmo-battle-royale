import * as Three from 'three'
import OBJLoader from 'three-obj-loader'
import MTLLoader from 'three-mtl-loader'

OBJLoader(Three)

const objLoader = new Three.OBJLoader()
const mtlLoader = new MTLLoader()
const noop = () => {}

function loadObj (resource) {
  return new Promise((resolve, reject) => {
    objLoader.load(resource, resolve, noop, reject)
  })
}

function loadMTL (resource) {
  return new Promise((resolve, reject) => {
    mtlLoader.load(resource, resolve, noop, reject)
  })
}

export default async (resource) => {
  const mtl = await loadMTL(`models/${resource}.mtl`)
  objLoader.setMaterials(mtl)
  const obj = await loadObj(`models/${resource}.obj`)
  return obj
}
