
import * as Three from 'three'
import Entity from './entity'
import Player from './player'
import Kefir from 'kefir'
import './main.styl'

let game = prompt('Enter the game you want to join: ')
let name = prompt('Enter You Name: ')

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

function applyUpdate (world, message) {
  const messageBuf = new DataView(message.data)
  const time = new Date(messageBuf.getUint64(0))
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
        let entityView = new DataView(messageBuf, i, Entity.size)
        let entityConstructor = entityConstructors[entityView.getUint8(i)]
        let doubles = getDoubles(entityView, i + 1)
        entities.push(
          entityConstructor(
            getVector(doubles),
            getQuaternion(doubles),
            getVector(doubles),
            getVector(doubles)
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

const renderer = new Three.WebGLRenderer({
  antialias: true
})
renderer.setSize(window.innerWidth, window.innerHeight)
document.body.appendChild(renderer.domElement)

const resize = Kefir.fromEvents(window, 'resize').map(evt => evt.target)
const camera = resize.map(
  ({ innerWidth, innerHeight }) => new Three.PerspectiveCamera(70, innerWidth / innerHeight, 0.01, 150)
)
resize.onValue(
  ({ innerWidth, innerHeight }) => renderer.resize(innerWidth, innerHeight)
)
const frames = Kefir.stream(emitter => {
  function doFrame () {
    emitter(new Date())
    requestAnimationFrame(doFrame)
  }
  requestAnimationFrame(doFrame)
})

function getWebSocket (game, name) {
  const proto = (location.protocol === 'http:') ? 'ws:' : 'wss:'
  const ws = new WebSocket(`${proto}//${location.host}/socket/${game}/${name}`)
  return ws
}

const ws = getWebSocket(game, name)
const updates = Kefir.fromEvents(ws, 'message')
const ticks = updates.scan(applyUpdate, initialWorld())

function addMesh (scene, mesh) {
  scene.add(mesh)
  return mesh
}

function updateScene ({ entities, camera }, { meshes, scene }) {
  const newMeshes = new Map()

  // adjust meshes based on entities, it doesn't matter if a mesh changes entities as long as
  // the entity type doesn't change
  for (let { position, quaternion, constructor, geometry, material } of entities) {
    let mesh = meshes.get(constructor).pop() || addMesh(scene, new Three.Mesh(geometry, material))
    mesh.position = position
    mesh.quaternion = quaternion
    if (!newMeshes.has(constructor)) {
      newMeshes.set(constructor, [])
    }
    newMeshes.get(constructor).push(mesh)
  }
  // remove remaining garbage meshes
  for (let meshList of meshes.values) {
    for (let mesh of meshList) {
      scene.remove(mesh)
    }
  }
  return {
    scene,
    meshes: newMeshes
  }
}

function extrapolate (frameTime, world) {
  const delta = frameTime - world.lastUpdated
  return {
    ...world,
    entities: world.entities.map(e => e.extrapolate(delta))
  }
}

function render ({ scene, camera }) {
  renderer.render(scene, camera)
}

// not sure I have to update or can create a scene each frame
const sceneFrames = Kefir.combine([frames], [ticks], extrapolate)
const scenes = Kefir.combine({ world: sceneFrames }, { camera: camera })
  .scan(updateScene, { meshes: new Map(), scene: new Three.Scene() })

scenes.onValue(render)

document.body.appendChild(renderer.domElement)
