object Library {
  def getJavaHomeDir(): String = {
    sys.env.getOrElse("JAVA_HOME", "NOT_FOUND")
  }
}
