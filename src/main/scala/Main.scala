import java.time.LocalDateTime
import java.time.format.DateTimeFormatter // https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
import scala.io.StdIn.readLine

object Main extends App {
  print("📝 Enter your name: ")
  val yourName = readLine() // User input from std. in.

  val currentTime = LocalDateTime.now()
  val datetimeFormatter = DateTimeFormatter.ofPattern("h:mm a") // https://www.digitalocean.com/community/tutorials/java-simpledateformat-java-date-format
  val formattedCurrentTime = currentTime.format(datetimeFormatter)

  println(s"😄 Hello $yourName, its $formattedCurrentTime")
}