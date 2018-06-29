import * as Three from 'three'
import Entity from './entity'

export default class Player extends Entity {
  static get geometry () {
    return new Three.BoxBufferGeometry(1, 1, 1)
  }
  static get material () {
    return new Three.MeshBasicMaterial({ color: 0x538EED })
  }
}
