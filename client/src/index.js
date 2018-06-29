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

renderer.setSize(window.innerWidth, window.innerHeight)
renderer.setPixelRatio(window.devicePixelRatio)
document.body.appendChild(renderer.domElement)

const ws = (() => {
  const proto = (location.protocol === 'http:') ? 'ws:' : 'wss:'
  const ws = new WebSocket(`${proto}//${location.host}/socket/${game}/${name}`)
  return ws
})()

function meshMap () {
  const meshes = new Map()
  meshes.set(Player, [])
  return meshes
}

function * getDoubles (dataView, i) {
  yield dataView.getFloat64(i)
  i += 8
}

const entityConstructors = {
  0: (...args) => new Player(...args)
}

function getVector (doubles) {
  return new Three.Vector3(
    doubles.next().value,
    doubles.next().value,
    doubles.next().value
  )
}

function getQuaternion (doubles) {
  return new Three.Quaternion(
    doubles.next().value,
    doubles.next().value,
    doubles.next().value,
    doubles.next().value
  )
}

function getQuatFromEuler (doubles) {
  const quat = new Three.Quaternion()
  quat.setFromEuler(new Three.Euler(
    doubles.next().value,
    doubles.next().value,
    doubles.next().value
  ))
  return quat
}

function applyUpdate (message) {
  const messageBuf = new DataView(message)
  const time = new Date(Number(messageBuf.getBigUint64(0)))
  switch (messageBuf.getUint8(8)) {
    case 0: // text notification
      const decoder = new TextDecoder('utf-8')
      const text = decoder.decode(new Uint8Array(message.data, 9))
      notifications.push({ text, time })
      return
    case 1: // world update
      entities = []
      for (let i = 9; i < messageBuf.byteLength; i += Entity.size) {
        let entityView = new DataView(message, i, Entity.size)
        let entityConstructor = entityConstructors[entityView.getUint8(i)]
        let doubles = getDoubles(entityView, i + 1)
        entities.push(
          entityConstructor(
            getVector(doubles),
            getQuaternion(doubles),
            getVector(doubles),
            getQuatFromEuler(doubles)
          )
        )
      }
      return
    default: // unrecognized message
      console.log('Unrecognized message')
      console.log(message.data)
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
  const reader = new FileReader()
  reader.onload = evt => applyUpdate(evt.target.result)
  return reader.readAsArrayBuffer(evt.data)
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
