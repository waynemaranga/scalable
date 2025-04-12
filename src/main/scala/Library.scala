object Library {
  def getJavaHomeDir(): String = {
    sys.env.getOrElse("JAVA_HOME", "NOT_FOUND") // reading environment variable
  }

  def getEnvVar(envVar: String) = { // function with args/params
    sys.env.getOrElse(envVar, "NOT_FOUND")
  }
}
