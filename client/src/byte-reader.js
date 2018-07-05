import * as Three from 'three'

const pattern = /\d\d?$/

export default function ByteReading (dataView, i = 0) {
  if (dataView instanceof ArrayBuffer) {
    dataView = new DataView(dataView)
  }
  function readBytes (prop, amount, ...args) {
    if (args.length === 0) {
      const ret = this[prop](i)
      i += amount
      return ret
    } else {
      return this[prop](...args)
    }
  }
  const methods = {
    bytesLeft () {
      return this.byteLength - i
    },
    setIndex (newI) {
      i = newI
    },
    getIndex () {
      return i
    },
    getVector () {
      return new Three.Vector3(
        this.getFloat32(),
        this.getFloat32(),
        this.getFloat32()
      )
    },
    getQuaternion () {
      const w = this.getFloat32()
      return new Three.Quaternion(
        this.getFloat32(),
        this.getFloat32(),
        this.getFloat32(),
        w
      )
    },
    getQuatFromEuler () {
      const quat = new Three.Quaternion()
      quat.setFromEuler(new Three.Euler(
        this.getFloat32(),
        this.getFloat32(),
        this.getFloat32()
      ))
      return quat
    }
  }
  const proxy = new Proxy(dataView, {
    get (obj, prop) {
      if (prop in methods) {
        return methods[prop].bind(proxy)
      }
      const arr = pattern.exec(prop)
      if (arr) {
        return readBytes.bind(obj, prop, +arr[0] / 8)
      }
      return obj[prop]
    }
  })
  return proxy
}
