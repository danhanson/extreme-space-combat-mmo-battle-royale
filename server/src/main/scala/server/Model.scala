package server

import scala.io.Source
import scala.util.parsing.combinator._
import scala.util.parsing.input._
import org.ode4j.ode._
import com.typesafe.scalalogging.StrictLogging

case class Model(vertexes: Array[Float], indexes: Array[Int]) {
  def geom(): DGeom = {
    val meshData = OdeHelper.createTriMeshData()
    meshData.build(vertexes, indexes)
    OdeHelper.createTriMesh(null, meshData, null, null, null)
  }
}

object Model extends JavaTokenParsers with StrictLogging {

  abstract class Matcher[T](parser: Parser[T]) {
    def unapply(str: String): Option[T] =
      parser(new CharSequenceReader(str)).map(Some(_)).getOrElse(None)
  }

  private def f = floatingPointNumber ^^ { _.toFloat }
  private def i = wholeNumber ^^ { _.toInt }

  val vertex: Parser[Float ~ Float ~ Float] = 'v' ~> f ~ f ~ f <~ f.?
  val vertexRef: Parser[Int] = i <~ ("//" ~ i | "/" ~ i ~ "/" ~ i).?
  val face: Parser[Seq[Int]] = 'f' ~> repN(3, vertexRef) | err("Did you triangulate the model when exporting?")

  object Vertex extends Matcher(vertex)
  object Face extends Matcher(face)

  def load(name: String): Model = {
    val vertexes = Array.newBuilder[Float]
    val faces = Array.newBuilder[Int]
    val source = loadSource(name)
    try {
      source.getLines().foreach {
        case Vertex(x ~ y ~ z) =>
          vertexes += x += y += z
        case Face(points) =>
          faces ++= points.map(_ - 1)
        case _ =>
          ()
      }
    } finally {
      source.close()
    }
    val vertexResult = vertexes.result()
    val facesResult = faces.result()
    logger.trace(s"vertexes:\n${vertexResult.mkString("\n")}\nFaces:\n${facesResult.mkString("\n")}")
    Model(vertexResult, facesResult)
  }

  private def loadSource(name: String): Source = {
    var source = Option.empty[Source]
    try {
      source = Option(getClass.getResourceAsStream("/models/${name}.obj")).map { input =>
        Source.fromInputStream(input)
      } orElse {
        Some(Source.fromFile(s"../models/$name.obj"))
      }
      source.get
    } catch {
      case e: Exception =>
        source.foreach(_.close())
        throw e
    }
  }
}