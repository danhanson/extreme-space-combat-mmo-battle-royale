import Entity from './entity'
import { load } from './obj-loader.js'

export default (async () => {
  const obj = await load('/models/player')
  return class Player extends Entity {
    static get resource () {
      return obj.clone()
    }
    static get id () {
      return 0
    }
  }
})()
