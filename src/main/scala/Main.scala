import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Main extends App {

  val currentTime = LocalDateTime.now()
  val datetimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  val formattedCurrentTime = currentTime.format(datetimeFormatter)

  println(s"Hello 😄, its $formattedCurrentTime")
}