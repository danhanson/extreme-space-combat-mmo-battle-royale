package server

import scala.util.Try
import scala.io.Source
import scala.util.parsing.combinator._
import scala.util.parsing.input._
import scala.collection.{mutable, BitSet}
import org.ode4j.ode._
import breeze.linalg._

case class Model(vertexes: Array[Float], indexes: Array[Int]) {
  def geom(): DGeom = {
    val meshData = OdeHelper.createTriMeshData()
    meshData.build(vertexes, indexes)
    OdeHelper.createTriMesh(null, meshData, null, null, null)
  }
}

object Model extends JavaTokenParsers {

  abstract class Matcher[T](parser: Parser[T]) {
    def unapply(str: String): Option[T] =
      parser(new CharSequenceReader(str)).map(Some(_)).getOrElse(None)
  }

  def f = floatingPointNumber ^^ { _.toFloat }
  def i = wholeNumber ^^ { _.toInt }

  val vertex: Parser[Float ~ Float ~ Float] = 'v' ~> f ~ f ~ f
  val normal: Parser[Float ~ Float ~ Float] = "vn" ~> f ~ f ~ f
  val vertexRef: Parser[Int] = i <~ ("//" | "/" ~ i ~ "/") ~ i
  val face: Parser[Seq[Int]] = 'f' ~> rep(vertexRef)

  object Vertex extends Matcher(vertex)
  object Normal extends Matcher(normal)
  object Face extends Matcher(face)

  sealed trait Result {

    /**
      * Checks if the parameters produced by solveIntersection are between 0
      * and 1. We ignore intersections from segments sharing vertices.
      */
    def segmentsIntersect: Boolean
  }

  case object NoSolution extends Result {
    def segmentsIntersect: Boolean = false
  }

  case class ManySolutions(ratio: Float, constant: Float) extends Result {
    def sy(sx: Float): Float =
      ratio * sx + constant

    def sx(sy: Float): Float =
      (sy - constant) / ratio

    def segmentsIntersect: Boolean = {
      val sy0 = sx(0)
      val sy1 = sx(1)
      0 < sy0 && sy0 < 1 || 0 < sy1 && sy1 < 1 || sy0 < 0 && sy1 > 1 || sy1 < 0 && sy0 > 1
    }
  }

  case class SingleSolution[T](sx: Float, sy: Float) extends Result {
    def segmentsIntersect: Boolean = {
      sx > 0 && sx < 1 && sy > 0 && sy < 1
    }
  }

   /**
    * Check if 2 lines intersect by parameterizing x and y and solving
    * the system of equations for when they are equal.
    * 
    * X(sx) = X1 + (X2 - X1) * sx
    * Y(sy) = Y1 + (Y2 - Y1) * sy
    * X(sx) = Y(sy)
    *
    * Returns S = [ sx, sy ], which can be used to determine how the lines itersect
    */
  def solveIntersection(
    x: Seq[DenseVector[Float]],
    y: Seq[DenseVector[Float]]
  ): Result = {

    // A = [ X2 - X1 | Y1 - Y2 ]
    val a = DenseMatrix.horzcat(Seq(
      x(1) - x(0),
      y(0) - y(1)
    ).map(_.asDenseMatrix.t): _*)

    // B = Y1 - X1
    val b = y(0) - x(0)

    // solution S is found by solving B = A * S
    // after LU decomposition, we get P * B = P * L * U * S
    val LU.LU(pivot, lower, upper) = LU(a)
    // P * B
    val pb = pivot * b

    // stores T = U * S
    val t = DenseVector.zeros[Float](2)

    // back substitution to find T by solving P * B = P * L * T
    for(col <- 0 until t.size) {
      t(col) = pb(col)
      val rowsBelow = col + 1 to -1
      pb(rowsBelow) -= t(col) * lower(rowsBelow, col)
    }

    // if remaining rows reduce to 0 = 0
    val hasSolution = pb(t.size to -1).forall(_ == 0)

    if(hasSolution) {
      // find S by solving T = U * S
      if(upper(1,::).t.forall(_ == 0)) {
        // many solutions S, top row is the relationship between sx and sy
        ManySolutions(upper(0, 1) / upper(0, 0), t(0) / upper(0, 0))
      } else {
        // one solution S
        val solution = upper(0 to 1,::) \ t
        SingleSolution(solution(0), solution(1))
      }
    } else {
      NoSolution // no solutions for T exist, there is no solution for S
    }
  }

  /**
   * Checks if the angle in the polygon constructed from the specified edges
   * is a convex angle. We count how many times the angle's bisector crosses
   * the polygon. If the count is odd, the angle is convex.
   */
  def isConvex(angle: Seq[DenseVector[Float]], edges: TraversableOnce[Seq[DenseVector[Float]]]): Boolean = {
    val bisector = Seq(angle(1), (angle(0) + angle(2)))
    val edgesCrossed = edges.count { edge =>
      // the bisector intersects with a segment (ignoring vertices) if 0 < sy < 1
      //
      // we only care about intersections in front of the angle where sx > 0
      //
      // we count intersections with the first vertex, where sy = 0, but not the
      // second, this avoids double-counting intersections when the bisector crosses
      // a vertex.
      //
      // we disregard the edge case where the bisector crosses a vertex without
      // entering/exiting the polygon, like what are the chances of that ever
      // happening?
      solveIntersection(edge, bisector) match {
        case SingleSolution(sx, sy) =>
          val isInFront = sx > 0
          val intersects = 0 <= sy && sy < 1
          isInFront && intersects
        case s@ManySolutions(ratio, constant) =>
          // same logic as before except sx and sy are interdependent
          // we analyze the extreme values, where sx or sy are 0 or 1
          val sx0 = s.sx(0)
          val sx1 = s.sx(1)
          val isInFront = sx0 > 0 || sx1 > 0
          val sy0 = s.sy(0)
          val sy1 = s.sy(1)
          isInFront && (
            0 <= sy0 && sy0 < 1 ||
            0 <= sy1 && sy1 < 1 ||
            sy0 < 0 && sy1 > 1 ||
            sy1 < 0 && sy0 > 1
          )
        case NoSolution =>
          false
      }
    }
    edgesCrossed % 2 == 1 // the angle is convex when bisector crosses polygon an odd number of times
  }

  /**
   * Checks if it is safe to use the specified angle as a triangle for the mesh.
   *
   * A triangle may be added to the mesh if it doesn't intersect any outside
   * edges or exits polygon.
   */
  def isTriangle(angle: Seq[DenseVector[Float]], edges: Traversable[Seq[DenseVector[Float]]]): Boolean = {
    val oppositeSegment = Seq(angle(0), angle(2))
    val isIntersecting = edges.find(solveIntersection(oppositeSegment, _).segmentsIntersect).isDefined
    !isIntersecting && isConvex(angle, edges)
  }

  def triangulate(vertexes: DenseMatrix[Float], points: Seq[Int]): Array[Int] = {

    var remainingPoints: BitSet = (0 until points.size).foldLeft(BitSet.empty) { _ + _ }
    val meshBuilder = Array.newBuilder[Int]
    while(remainingPoints.size >= 3) {
      val angles = remainingPoints.view.map { current =>
        val prev = remainingPoints.until(current).lastOption.getOrElse(remainingPoints.last)
        val next = remainingPoints.from(current + 1).headOption.getOrElse(remainingPoints.head)
        current -> Seq(vertexes(::,prev), vertexes(::,current), vertexes(::,next))
      }
      val edges = remainingPoints.view.map { current =>
        val next = remainingPoints.from(current + 1).headOption.getOrElse(remainingPoints.head)
        Seq(vertexes(::,current), vertexes(::,next))
      }
      val (point, angle) = angles.find {
        case (point, angle) =>
          isTriangle(angle, edges)
      }.getOrElse(
        throw new IllegalArgumentException("Received invalid polygon")
      )
      remainingPoints -= point
      meshBuilder +=  (point + points.size - 1) % points.size += point += (point + 1) % points.size
    }
    meshBuilder.result
  }

  def load(name: String): Model = {
    val vertexes = Array.newBuilder[Float]
    val faces = Array.newBuilder[Seq[Int]]
    val source = loadSource(name)
    try {
      source.getLines().foreach {
        case Vertex(x ~ y ~ z) =>
          vertexes += (x - 1) += (y - 1) += (z - 1)
        case Face(points) =>
          faces += points.map(_ - 1)
        case _ =>
          ()
      }
    } finally {
      source.close()
    }
    val vertexResult = vertexes.result()
    val vertexMatrix = new DenseMatrix(3, vertexResult.length / 3, vertexResult)
    val mesh = faces.result().map(triangulate(vertexMatrix, _)).flatten
    Model(vertexResult, mesh)
  }

  private def loadSource(name: String): Source = {
    var source = Option.empty[Source]
    try {
      source = Option(getClass.getResourceAsStream("/models/${name}.obj")).map { input =>
        Source.fromInputStream(input)
      } orElse {
        Some(Source.fromFile(s"../models/${name}.obj"))
      }
      source.get
    } catch {
      case e: Exception =>
        source.map(_.close())
        throw e
    }
  }
}