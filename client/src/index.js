'strict mode'
import * as Three from 'three'
import Entity from './entity'
import Player from './player'
import './main.styl'

let game = prompt('Enter the game you want to join: ')
let name = prompt('Enter You Name: ')
const camera = new Three.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.01, 15000)
camera.position.z = 300
const scene = new Three.Scene()
let entities = []
let notifications = []
let lastUpdated = new Date()
let meshes = meshMap()
const renderer = new Three.WebGLRenderer({
  antialias: true
})
let input = {

}

renderer.setSize(window.innerWidth, window.innerHeight)
renderer.setPixelRatio(window.devicePixelRatio)
document.body.appendChild(renderer.domElement)

const ws = (() => {
  const proto = (location.protocol === 'http:') ? 'ws:' : 'wss:'
  const ws = new WebSocket(`${proto}//${location.host}/socket/${game}/${name}`)
  ws.binaryType = 'arraybuffer'
  return ws
})()

function meshMap () {
  const meshes = new Map()
  meshes.set(Player, [])
  return meshes
}

function * getFloats (dataView, i) {
  while (i < dataView.length) {
    yield dataView.getFloat32(i)
    i += 8
  }
}

const entityConstructors = {
  0: (...args) => new Player(...args)
}

function getVector (numbers) {
  return new Three.Vector3(
    numbers.next().value,
    numbers.next().value,
    numbers.next().value
  )
}

function getQuaternion (numbers) {
  return new Three.Quaternion(
    numbers.next().value,
    numbers.next().value,
    numbers.next().value,
    numbers.next().value
  )
}

function getQuatFromEuler (numbers) {
  const quat = new Three.Quaternion()
  quat.setFromEuler(new Three.Euler(
    numbers.next().value,
    numbers.next().value,
    numbers.next().value
  ))
  return quat
}

function applyUpdate (message) {
  const entitySize = (3 * 3 + 4) * 4 // 3 vectors and a quaternion
  const messageView = new DataView(message)
  const time = new Date(Number(messageView.getBigUint64(0)))
  switch (messageView.getUint8(8)) {
    case 0: // text notification
      const decoder = new TextDecoder('utf-8')
      const text = decoder.decode(new Uint8Array(message.data, 9))
      notifications.push({ text, time })
      return
    case 1: // world update
      entities = []
      for (let i = 9; i < messageView.byteLength; i += entitySize) {
        let entityConstructor = entityConstructors[messageView.getUint8(i)]
        let numbers = getFloats(messageView, i + 1)
        entities.push(
          entityConstructor(
            getVector(numbers),
            getQuaternion(numbers),
            getVector(numbers),
            getQuatFromEuler(numbers)
          )
        )
      }
      return
    default: // unrecognized message
      console.warn('Unrecognized message')
      console.warn(message.data)
      notifications.push(
        { text: 'Received unrecognized message, see console for details', time }
      )
  }
}

window.addEventListener('resize',
  ({ target: { innerWidth, innerHeight } }) => {
    renderer.resize(innerWidth, innerHeight)
    camera.aspect = innerWidth / innerHeight
    camera.updateProjectionMatrix()
  }
)

ws.addEventListener('message', evt => {
  applyUpdate(evt.data)
})

function extrapolate (frameTime) {
  const delta = (frameTime - lastUpdated) / 1000
  for (let e of entities) {
    e.extrapolate(delta)
  }
}

function filterNotifications (frameTime) {
  const thirtySeconds = 30 * 60 * 1000
  notifications = notifications.filter(n => (frameTime - n.time) < thirtySeconds)
}

function addMesh (mesh) {
  scene.add(mesh)
  return mesh
}

function updateScene (frameTime) {
  const newMeshes = meshMap()
  extrapolate(frameTime)
  filterNotifications(frameTime)

  // adjust meshes based on entities, it doesn't matter if a mesh changes entities as long as
  // the entity type doesn't change
  for (let { position, quaternion, constructor, geometry, material } of entities) {
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
  const frameTime = new Date()
  updateScene(frameTime)
  renderer.render(scene, camera)
  requestAnimationFrame(doFrame)
}
requestAnimationFrame(doFrame)

function sendUpdate () {
  const buffer = new ArrayBuffer(2 * 3 * 4) // 2 3d vectors
  const view = new DataView(buffer)
  view.setFloat32(0, input.left - input.right)
  view.setFloat32(8, input.up - input.down)
  view.setFloat32(16, input.forward - input.backword)
  view.setFloat32(24, input.rotateLeft - input.rotateRight)
  view.setFloat32(32, input.rotateUp - input.rotateDown)
  view.setFloat32(40, input.spinLeft - input.spinRight)
  ws.send(buffer)
}

function updateInput (code, val) {
  switch (code) {
    case 'ArrowRight':
    case 'KeyD':
      input.rotateDown = 1
      break
    case 'ArrowUp':
    case 'KeyW':
      input.rotateUp = 1
      break
    case 'ArrowLeft':
    case 'KeyA':
      input.rotateLeft = 1
      break
    case 'ArrowDown':
    case 'KeyS':
      input.rotateDown = 1
      break
    case 'ShiftLeft':
    case 'ShiftRight':
      input.forward = 1
      break
    case 'ControlLeft':
    case 'ControlRight':
      input.backword = 1
      break
    default:
      return // don't send update for garbage keys
  }
  sendUpdate()
}

document.addEventListener('keydown', evt => {
  updateInput(evt.code, true)
})
document.addEventListener('keyup', evt => {
  updateInput(evt.code, false)
})
