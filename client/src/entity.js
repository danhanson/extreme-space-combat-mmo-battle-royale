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

  /**
   * Guesses current position of the entity based on time since last tick.
   * Uses basic linear interpolation on velocity and rotation.
   * Doesn't seem to work so it's commented out.
   *
   * @param {Number} delta: time in seconds since last tick
   */
  extrapolate (delta) {
    // this.position.add(this.velocity.clone().multiplyScalar(delta))
    // while (delta > 1) {
    //  this.quaternion.multiply(this.rotation)
    //  delta -= 1
    // }
    // this.quaternion.slerp(this.rotation.clone().multiply(this.quaternion), delta)
  }
}
