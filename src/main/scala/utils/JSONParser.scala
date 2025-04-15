/** JSON */
package utils

import java.io.{File, PrintWriter}
import scala.io.Source
import scala.util.{Try, Success, Failure}
import org.json4s._ // https://medium.com/@k.yadukrishnan/json-parsing-using-json4s-in-scala-32eeb4516e06
import org.json4s.native.JsonMethods._ // https://queirozf.com/entries/json4s-examples-common-basic-operations-using-jackson-as-backend
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write

object JSONParser {
  // TODO: develop JSON schemas or schema generator/validator
  /** Using JSON4s implicit formats as the default formats. */
  implicit val formats: Formats = DefaultFormats

  /** */
  def parseFile(filePath: String): Either[String, JValue] = {
    Try {
      val fileContent = Source.fromFile(filePath).mkString
      parse(fileContent)
    } match {
      case Success(json)      => Right(json)
      case Failure(exception) => Left(s"${exception.getMessage}")
    }

  }

  /** */
  def getValue(
      json: JValue,
      path: Seq[Any]
  ): Either[String, org.json4s.JValue] = {
    Try {
      path.foldLeft(json) {
        case (current, key: String) => current \ key
        case (current, index: Int)  => current(index)
        case (_, invalid) => throw new IllegalArgumentException(s"$invalid")
      }
    } match {
      case Success(result) =>
        if (result == JNothing) Left("PATH NOT FOUND")
        else Right(result)
      case Failure(exception) => Left(s"${exception.getMessage}")
    }
  }

  /** */
  def saveToFile(json: JValue, filePath: String): Either[String, String] = {
    Try {
      val writer = new PrintWriter(new File(filePath))
      try {
        writer.write(pretty(render(json)))
        s"✅ JSON written: $filePath"
      } finally {
        writer.close()
      }
    } match {
      case Success(message) => Right(message)
      case Failure(e) => Left(s"Error saving JSON to file: ${e.getMessage}")
    }
  }

  /** */
  def saveMapToFile(
      map: Map[String, Any],
      filePath: String
  ): Either[String, String] = {
    Try {
      val writer = new PrintWriter(new File(filePath))
      try {
        writer.write(write(map))
        s"✅ JSON written: $filePath"
      } finally {
        writer.close()
      }
    } match {
      case Success(message) => Right(message)
      case Failure(e) => Left(s"Error saving JSON to file: ${e.getMessage}")
    }
  }
}
