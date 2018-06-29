import bindInput from './input'
import bindOutput from './output'
import animateOutput from './animate'
import './main.styl'

let game = prompt('Enter the game you want to join: ')
let name = prompt('Enter You Name: ')

const ws = (() => {
  const proto = (location.protocol === 'http:') ? 'ws:' : 'wss:'
  const ws = new WebSocket(`${proto}//${location.host}/socket/${game}/${name}`)
  ws.binaryType = 'arraybuffer'
  return ws
})()

animateOutput(bindOutput(ws))
bindInput(ws)
