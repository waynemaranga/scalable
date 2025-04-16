import java.time.LocalDateTime
import java.time.format.DateTimeFormatter // https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
import scala.io.StdIn.readLine
import ai.AzureOpenAIClient

object Main extends App {
  print("📝 Enter your name: ")
  val yourName = readLine() // User input from std. in.

  val currentTime = LocalDateTime.now()
  val datetimeFormatter = DateTimeFormatter.ofPattern(
    "h:mm a" // / https://www.digitalocean.com/community/tutorials/java-simpledateformat-java-date-format
  )
  val formattedCurrentTime = currentTime.format(datetimeFormatter)
  val javaHomeDirectory = Library.getJavaHomeDir()

  println(s"😄 Hello $yourName, its $formattedCurrentTime!")
  // println(Library.getJavaHomeDir())
  println(s"☕ Your Java home directory is $javaHomeDirectory")

  print("🔣 Select an environment variable: ")
  var envVarName = readLine() // returns string or null
  var envVarValue = Library.getEnvVar(envVarName)
  println(s"ℹ️ The env. var. $envVarName is: $envVarValue")

  // 🔮 Use Azure OpenAI here
  val aiMessage = AzureOpenAIClient.complete(s"Say hello in very verbose terms to $yourName")
  println(s"🤖 AzureOpenAI says: $aiMessage")

  HttpServer.start() // 🛫

  println("👋 Goodbye!")
}
