package server

import org.ode4j.math.DVector3C

case class ClientInput(
  force: DVector3C,
  torque: DVector3C
)
