import * as Three from 'three'

export default class Entity {
  static get geometry () {
    return new Three.BoxBufferGeometry(1, 1, 1)
  }
  static get material () {
    return new Three.MeshBasicMaterial({ color: 0x538EED })
  }
  static get size () {
    return 1 + (3 * 3 + 4) * 64
  }
  constructor (position = new Three.Vector3(0, 0, 0), quaternion = new Three.Quaternion(0, 0, 0, 1), velocity = new Three.Vector3(0, 0, 0), rotation = new Three.Quaternion(0, 0, 0, 1)) {
    this.position = position
    this.quaternion = quaternion
    this.velocity = velocity
    this.rotation = rotation
  }

  extrapolate (delta) {
    const position = this.position.clone().add(this.linearVelocity.clone().multiplyScalar(delta))
    const quaternion = this.quaternion.clone()
    while (delta > 1) {
      quaternion.multiply(this.angularVelocity)
      delta -= 1
    }
    quaternion.slerp(this.angularVelocity.clone().multiply(this.mesh.quaternion), delta)
    return new this.constructor(position, quaternion, this.velocity, this.rotation)
  }
}
