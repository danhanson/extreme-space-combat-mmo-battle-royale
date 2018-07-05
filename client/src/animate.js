/**
 * Animates the output provided by output.js
 * @param {Object} Object returned by output.js
 */
import * as Three from 'three'
import PlayerPromise from './player'

export default async function animateOutput ({ entities, notifications, lastUpdated, player }) {
  const Player = await PlayerPromise

  const camera = new Three.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.01, 15000)
  window.camera = camera
  camera.position.copy(new Three.Vector3(0, 0, -3))
  const renderer = new Three.WebGLRenderer({
    antialias: true
  })
  const scene = new Three.Scene()
  scene.add(new Three.AxesHelper(5))
  scene.add(new Three.AmbientLight(0x505050))
  scene.add(new Three.DirectionalLight(0xffffff, 0.5))
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

  function addCopy (obj) {
    scene.add(obj)
    return obj
  }

  function updateScene (frameTime) {
    const newMeshes = meshMap()
    extrapolate(frameTime)
    filterNotifications(frameTime)
    const {
      position: playerPosition,
      quaternion: playerQuaternion
    } = player()
    // reverse the mesh arrays so meshes stay in order when popped and pushed
    for (let array of Object.values(meshes)) {
      array.reverse()
    }

    // adjust meshes based on entities, it doesn't matter if a mesh changes entities as long as
    // the entity type doesn't change
    for (let { position, quaternion, id, resource } of entities()) {
      let object = meshes[id].pop() || addCopy(resource)
      // console.log(position)
      // console.log(quaternion)
      object.position.copy(position).sub(playerPosition)
      object.quaternion.copy(quaternion).multiply(playerQuaternion)
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
    const player = meshes[0][0]
    if (player) {
      window.player = player
      camera.up.copy(player.up)
      const position = new Three.Vector3(0, -3, 2) // 3 behind
      position.applyMatrix4(player.matrix)
      camera.position.copy(position)
      camera.lookAt(player.position.clone().add(player.up.clone().multiplyScalar(2)))
    }
    renderer.render(scene, camera)
    requestAnimationFrame(doFrame)
  }
  const vec = new Three.Vector3()
  camera.getWorldDirection(vec)
  requestAnimationFrame(doFrame)
}

window.fraction = 1
