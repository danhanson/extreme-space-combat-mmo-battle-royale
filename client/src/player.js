import * as Three from 'three'
import Entity from './entity'
import load from './obj-loader.js'

export default (async () => {
  const obj = await load('player')
  return class Player extends Entity {
    static get resource () {
      return obj
    }
    static get id () {
      return 0
    }
    static get geometry () {
      return new Three.BoxBufferGeometry(1, 1, 1)
    }
    static get material () {
      return new Three.MeshBasicMaterial({ color: 0x538EED })
    }
  }
})()
