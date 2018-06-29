/**
 * Animates the output provided by output.js
 * @param {Object} Object returned by output.js
 */
import * as Three from 'three'
import Player from './player'

export default function animateOutput ({ entities, notifications, lastUpdated }) {
  const camera = new Three.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.01, 15000)
  camera.position.z = 3
  const renderer = new Three.WebGLRenderer({
    antialias: true
  })
  const scene = new Three.Scene()
  let meshes = meshMap()
  renderer.setSize(window.innerWidth, window.innerHeight)
  renderer.setPixelRatio(window.devicePixelRatio)
  document.body.appendChild(renderer.domElement)

  window.addEventListener('resize',
    ({ target: { innerWidth, innerHeight } }) => {
      renderer.resize(innerWidth, innerHeight)
      camera.aspect = innerWidth / innerHeight
      camera.updateProjectionMatrix()
    }
  )

  function meshMap () {
    const meshes = new Map()
    meshes.set(Player, [])
    return meshes
  }

  function extrapolate (frameTime) {
    const delta = (frameTime - lastUpdated()) / 1000 / 1.890967 // a magic number with no known origin
    for (let e of entities()) {
      e.extrapolate(delta)
    }
  }

  function filterNotifications (frameTime) {
    const thirtySeconds = 30 * 60 * 1000
    return notifications().filter(n => (frameTime - n.time) < thirtySeconds)
  }

  function addMesh (mesh) {
    scene.add(mesh)
    return mesh
  }

  function updateScene (frameTime) {
    const newMeshes = meshMap()
    extrapolate(frameTime)
    filterNotifications(frameTime)

    // reverse the mesh arrays so meshes stay in order when popped and pushed
    for (let array of meshes.values()) {
      array.reverse()
    }

    // adjust meshes based on entities, it doesn't matter if a mesh changes entities as long as
    // the entity type doesn't change
    for (let { position, quaternion, constructor, geometry, material } of entities()) {
      let mesh = meshes.get(constructor).pop() || addMesh(new Three.Mesh(geometry, material))
      mesh.position.copy(position)
      mesh.quaternion.copy(quaternion)
      newMeshes.get(constructor).push(mesh)
    }
    // remove remaining garbage meshes
    for (let meshList of meshes.values()) {
      for (let mesh of meshList) {
        scene.remove(mesh)
      }
    }
    meshes = newMeshes
  }

  function doFrame () {
    const frameTime = performance.timeOrigin + performance.now()
    updateScene(frameTime)
    renderer.render(scene, camera)
    requestAnimationFrame(doFrame)
  }
  requestAnimationFrame(doFrame)
}

window.fraction = 1
