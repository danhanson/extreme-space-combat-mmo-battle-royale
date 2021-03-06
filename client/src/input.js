/**
 * Binds user input to the specified web socket. Should be used for any input
 * used by the physics engine on the server. Should not be handling
 * interaction with ui.
 *
 * @param {WebSocket} ws
 */
export default function bindInput (ws) {
  const input = {
    left: 0,
    right: 0,
    up: 0,
    down: 0,
    forward: 0,
    backward: 0,
    rotateUp: 0,
    rotateDown: 0,
    rollRight: 0,
    rollLeft: 0,
    turnRight: 0,
    turnLeft: 0,
    shoot: 0
  }

  let pressedKeys = new Set()

  /**
   * Sends the input to the server where it's used for
   * future tick calculations until the next update is
   * sent. Sends two vectors, one for position and other
   * for rotation.
   */
  function sendUpdate () {
    const buffer = new ArrayBuffer(6 * 4) // 2 3d vectors
    const view = new DataView(buffer)
    view.setFloat32(0, input.right - input.left)
    view.setFloat32(4, input.forward - input.backward)
    view.setFloat32(8, input.up - input.down)
    view.setFloat32(12, input.rotateUp - input.rotateDown)
    view.setFloat32(16, input.rollRight - input.rollLeft)
    view.setFloat32(20, input.turnLeft - input.turnRight)
    view.setFloat32(24, input.shoot)
    ws.send(buffer)
  }

  function updateInput (code, val) {
    switch (code) {
      case 'ArrowRight':
      case 'KeyD':
        input.turnRight = val
        break
      case 'ArrowUp':
      case 'KeyW':
        input.rotateUp = val
        break
      case 'ArrowLeft':
      case 'KeyA':
        input.turnLeft = val
        break
      case 'ArrowDown':
      case 'KeyS':
        input.rotateDown = val
        break
      case 'ShiftLeft':
      case 'ShiftRight':
        input.forward = val
        break
      case 'ControlLeft':
      case 'ControlRight':
        input.backward = val
        break
      case 'KeyQ':
        input.rollLeft = val
        break
      case 'KeyE':
        input.rollRight = val
        break
      case 'Space':
        input.shoot = val
        break
      default:
        return // don't send update for garbage keys
    }
    sendUpdate()
  }
  document.addEventListener('keydown', evt => {
    if (pressedKeys.has(evt.code)) { // filter out duplicate presses
      return
    }
    pressedKeys.add(evt.code)
    updateInput(evt.code, 100)
  })
  document.addEventListener('keyup', evt => {
    if (pressedKeys.has(evt.code)) {
      pressedKeys.delete(evt.code)
      updateInput(evt.code, 0)
    }
  })
}
