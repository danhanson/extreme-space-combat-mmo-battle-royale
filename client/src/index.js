
import * as Three from 'three'
import Entity from './entity'
import Player from './player'
import Kefir from 'kefir'
import './main.styl'

let game = prompt('Enter the game you want to join: ')
let name = prompt('Enter You Name: ')

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

function applyUpdate (world, message) {
  const messageBuf = new DataView(message)
  const time = new Date(Number(messageBuf.getBigUint64(0)))
  switch (messageBuf.getUint8(8)) {
    case 0: // text notification
      const decoder = new TextDecoder('utf-8')
      const text = decoder.decode(new Uint8Array(message.data, 9))
      return {
        ...world,
        notifications: [...world.notifications, { text, time }]
      }
    case 1: // world update
      const entities = []
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
      return {
        ...world,
        entities,
        lastUpdated: time
      }
    default: // unrecognized message
      console.log('Unrecognized message')
      console.log(message.data)
      return {
        ...world,
        notifications: [
          ...world.notifications,
          { text: 'Received unrecognized message, see console for details', time }
        ]
      }
  }
}

function initialWorld () {
  return {
    entities: [],
    notifications: [],
    lastUpdated: new Date()
  }
}

const camera = new Three.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.01, 15000)
camera.position.z = 300
const renderer = new Three.WebGLRenderer({
  antialias: true
})
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

const frames = Kefir.stream(emitter => {
  function doFrame () {
    emitter.emit(new Date())
    requestAnimationFrame(doFrame)
  }
  requestAnimationFrame(doFrame)
})

function getWebSocket (game, name) {
  const proto = (location.protocol === 'http:') ? 'ws:' : 'wss:'
  const ws = new WebSocket(`${proto}//${location.host}/socket/${game}/${name}`)
  return ws
}

const updates = Kefir.stream(emitter => {
  const ws = getWebSocket(game, name)
  ws.addEventListener('message', evt => {
    const reader = new FileReader()
    reader.onload = evt => emitter.emit(evt.target.result)
    return reader.readAsArrayBuffer(evt.data)
  })
})
const ticks = updates.scan(applyUpdate, initialWorld())

function addMesh (scene, mesh) {
  scene.add(mesh)
  return mesh
}

function updateScene ({ meshes, scene }, { entities }) {
  const newMeshes = meshMap()

  // adjust meshes based on entities, it doesn't matter if a mesh changes entities as long as
  // the entity type doesn't change
  for (let { position, quaternion, constructor, geometry, material } of entities) {
    let mesh = meshes.get(constructor).pop() || addMesh(scene, new Three.Mesh(geometry, material))
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
  return {
    scene,
    camera,
    meshes: newMeshes
  }
}

function extrapolate (frameTime, world) {
  const delta = (frameTime - world.lastUpdated) / 1000
  return {
    ...world,
    entities: world.entities.map(e => e.extrapolate(delta))
  }
}

function render ({ scene, camera }) {
  if (camera) {
    renderer.render(scene, camera)
  }
}

const sceneFrames = Kefir.combine([frames], [ticks], extrapolate)
const scenes = sceneFrames.scan(updateScene, { meshes: meshMap(), scene: new Three.Scene() })

scenes.onValue(render)

document.body.appendChild(renderer.domElement)
