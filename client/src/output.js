/**
 * Stores updated date from the server sent through the specified web socket.
 * Use the methods of the returned object to receive most up-to-date game
 * information.
 *
 * @param {WebSocket} the websocket events are listened from
 */
import * as Three from 'three'
import PlayerPromise from './player'

const entitySize = 1 + (3 * 3 + 4) * 4 // type tag, 3 vectors and a quaternion
const decoder = new TextDecoder('utf-8')

async function loadResources () {
  const Player = await PlayerPromise
  return {
    [Player.id]: (...args) => new Player(...args)
  }
}

export default async function bindOutput (ws) {
  const entityConstructors = await loadResources()

  let entities = []
  let notifications = []
  let lastUpdated = performance.timeOrigin + performance.now()

  function * getFloats (dataView, i) {
    while (i < dataView.byteLength) {
      yield dataView.getFloat32(i)
      i += 4
    }
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
    const messageView = new DataView(message)
    const time = Number(messageView.getBigUint64(0))
    switch (messageView.getUint8(8)) {
      case 0: // text notification
        const text = decoder.decode(new Uint8Array(message.data, 9))
        notifications.push({ text, time })
        return
      case 1: // world update
        entities = []
        lastUpdated = time
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
    }
  }
}
