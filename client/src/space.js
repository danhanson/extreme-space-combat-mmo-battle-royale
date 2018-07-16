import * as Three from 'three'

const loader = new Three.TextureLoader()

const path = dir => `models/ame_nebula/purplenebula_${dir}.png`

function planeModel (dir) {
  return new Three.MeshBasicMaterial({
    map: loader.load(path(dir)),
    side: Three.DoubleSide
  })
}

export default (() => {
  const material = new Three.MeshFaceMaterial(
    [
      planeModel('bk'),
      planeModel('ft'),
      planeModel('up'),
      planeModel('dn'),
      planeModel('rt'),
      planeModel('lf')
    ]
  )
  const box = new Three.Mesh(new Three.BoxBufferGeometry(50000, 50000, 50000), material)
  box.frustumCulled = false
  return box
})()
