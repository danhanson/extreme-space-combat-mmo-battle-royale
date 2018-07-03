import * as Three from 'three'
import Entity from './entity'
import OBJLoader from './obj-loader.js'

export default new Promise((resolve, reject) => {
  const loader = new OBJLoader()
  loader.load('models/player.obj', model => {
    resolve(
      class Player extends Entity {
        static get resource () {
          return model
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
    )
  }, x => x, error => reject(error))
})
