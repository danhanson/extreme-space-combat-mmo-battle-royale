/**
 * Animates the output provided by output.js
 * @param {Object} Object returned by output.js
 */
import * as Three from 'three'
import PlayerPromise from './player'
import SpacePromise from './space'

const one = new Three.Vector3(1, 1, 1)
const zero = new Three.Vector3(0, 0, 0)

export default async function animateOutput ({ entities, notifications, lastUpdated, player }) {
  const Player = await PlayerPromise
  const space = await SpacePromise

  const camera = new Three.PerspectiveCamera(80, window.innerWidth / window.innerHeight, 0.01, 100000)
  camera.position.copy(new Three.Vector3(0, -3, 2))
  camera.lookAt(new Three.Vector3(0, 0, 1))
  const renderer = new Three.WebGLRenderer({
    antialias: true
  })
  const scene = new Three.Scene()
  scene.add(space)
  scene.add(new Three.AmbientLight(0x666666))
  scene.add(new Three.DirectionalLight(0xFFFFFF, 0.5))
  let meshes = meshMap()
  renderer.setSize(window.innerWidth, window.innerHeight)
  renderer.setPixelRatio(window.devicePixelRatio)
  const playerObject = Player.resource
  playerObject.position.copy(zero)
  window.player = playerObject
  window.camera = camera
  scene.add(playerObject)

  document.body.appendChild(renderer.domElement)

  window.addEventListener('resize',
    ({ target: { innerWidth, innerHeight } }) => {
      renderer.setSize(innerWidth, innerHeight)
      renderer.setPixelRatio(window.devicePixelRatio)
      camera.aspect = innerWidth / innerHeight
      camera.updateProjectionMatrix()
    }
  )

  function meshMap () {
    return {
      [Player.id]: []
    }
  }

  function extrapolate (frameTime) {
    const delta = (frameTime - lastUpdated()) / 1000
    for (let e of entities()) {
      e.extrapolate(delta)
    }
  }

  function filterNotifications (frameTime) {
    const thirtySeconds = 30 * 60 * 1000
    return notifications().filter(n => (frameTime - n.time) < thirtySeconds)
  }

  function add (obj) {
    obj.matrixAutoUpdate = false
    scene.add(obj)
    return obj
  }

  function getPlayerViewMatrix () {
    const {
      position,
      quaternion
    } = player()
    const transform = new Three.Matrix4()
    transform.compose(position, quaternion, one)
    const view = new Three.Matrix4()
    view.getInverse(transform, true)
    return view
  }

  function updateScene (frameTime) {
    const newMeshes = meshMap()
    extrapolate(frameTime)
    filterNotifications(frameTime)
    // reverse the mesh arrays so meshes stay in order when popped and pushed
    for (let array of Object.values(meshes)) {
      array.reverse()
    }
    const playerView = getPlayerViewMatrix()
    space.setRotationFromMatrix(playerView)
    // adjust meshes based on entities, it doesn't matter if a mesh changes entities as long as
    // the entity type doesn't change
    for (let { position, quaternion, id, resource } of entities()) {
      let object = meshes[id].pop() || add(resource)
      object.matrix.compose(position, quaternion, one)
      object.matrix.premultiply(playerView)
      newMeshes[id].push(object)
    }
    // remove remaining garbage meshes
    for (let meshList of Object.values(meshes)) {
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
