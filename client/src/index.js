import bindInput from './input'
import bindOutput from './output'
import animateOutput from './animate'
import * as Three from 'three'
import './main.styl'

Three.Object3D.DefaultUp = new Three.Vector3(0, 0, 1)

let game = prompt('Enter the game you want to join: ')
let name = prompt('Enter You Name: ')

const ws = (() => {
  const proto = (location.protocol === 'http:') ? 'ws:' : 'wss:'
  const ws = new WebSocket(`${proto}//${location.host}/socket/${game}/${name}`)
  ws.binaryType = 'arraybuffer'
  return ws
})()

bindInput(ws)

bindOutput(ws).then(output => {
  animateOutput(output)
})
