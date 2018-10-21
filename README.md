# Installation

1. Install [sbt](https://www.scala-sbt.org/download.html) and [Node.js](https://nodejs.org/en/download/)

2. Use npm to install yarn:

`npm install -g yarn`

3. Install and build the website in the client folder:

```
cd client
yarn install
yarn build
```

4. Compile and run the server:

```
cd server
sbt run
```

# Gameplay

By default, the server runs on [http://127.0.0.1:8080/](http://127.0.0.1:8080).

Visit, then choose a game and username.

# Controls

* WASD to control direction you are facing
* QE to rotate
* Shift/Control to accelerate/decelerate
