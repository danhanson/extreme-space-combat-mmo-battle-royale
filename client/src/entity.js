import * as Three from 'three'

export default class Entity {
  get resource () {
    return this.constructor.resource
  }
  get id () {
    return this.constructor.id
  }
  constructor (position = new Three.Vector3(0, 0, 0), quaternion = new Three.Quaternion(0, 0, 0, 1), velocity = new Three.Vector3(0, 0, 0), rotation = new Three.Quaternion(0, 0, 0, 1)) {
    this.position = position
    this.quaternion = quaternion
    this.velocity = velocity
    this.rotation = rotation
  }

  extrapolate (delta) {
    // this.position.add(this.velocity.clone().multiplyScalar(delta))
    // while (delta > 1) {
    //  this.quaternion.multiply(this.rotation)
    //  delta -= 1
    // }
    // this.quaternion.slerp(this.rotation.clone().multiply(this.quaternion), delta)
  }
}
