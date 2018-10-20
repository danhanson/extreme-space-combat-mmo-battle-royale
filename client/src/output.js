/**
 * Provides a function that returns an object containing updated data from the
 * server received from the specified web socket. Use the methods of the returned
 * object to receive most up-to-date game information.
 */
import * as Three from 'three'
import PlayerPromise from './player'
import ByteReader from './byte-reader'

const decoder = new TextDecoder('utf-8')

async function loadResources () {
  const Player = await PlayerPromise
  return {
    [Player.id]: (...args) => new Player(...args)
  }
}

/**
 * Returns the output object used to read data from the server.
 *
 * @param {WebSocket} the websocket events are listened from
 */
export default async function bindOutput (ws) {
  const entityConstructors = await loadResources()

  let entities = []
  let player = {
    position: new Three.Vector3(0, 0, 0),
    quaternion: new Three.Quaternion(0, 0, 0, 1),
    velocity: new Three.Vector3(0, 0, 0),
    rotation: new Three.Vector3(0, 0, 0)
  }
  let notifications = []
  let lastUpdated = performance.timeOrigin + performance.now()

  function applyUpdate (message) {
    const reader = ByteReader(message)
    const time = Number(reader.getBigUint64())
    switch (reader.getUint8()) {
      case 0: // text notification
        const text = decoder.decode(new Uint8Array(message.data, reader.getIndex()))
        notifications = [...notifications, { text, time }]
        return
      case 1: // world update
        entities = []
        lastUpdated = time
        reader.getUint8() // discard tag
        player.position = reader.getVector()
        player.quaternion = reader.getQuaternion()
        player.velocity = reader.getVector()
        player.rotation = reader.getQuatFromEuler()
        while (reader.bytesLeft() > 0) {
          let entityConstructor = entityConstructors[reader.getUint8()]
          entities.push(
            entityConstructor(
              reader.getVector(),
              reader.getQuaternion(),
              reader.getVector(),
              reader.getQuatFromEuler()
            )
          )
        }
        return
      default: // unrecognized message
        console.warn('Unrecognized message')
        console.warn(message.data)
        notifications = [
          ...notifications,
          { text: 'Received unrecognized message, see console for details', time }
        ]
    }
  }
  ws.addEventListener('message', evt => {
    applyUpdate(evt.data)
  })
  return {
    entities () {
      return entities
    },
    notifications () {
      return notifications
    },
    lastUpdated () {
      return lastUpdated
    },
    player () {
      return player
    }
  }
}
