package services

import java.io.File
import java.nio.file.Files
import javax.inject.{Inject, Singleton}

import play.api.{Configuration, Environment, Logger}

@Singleton
class FileService @Inject()(environment: Environment,
                            configuration: Configuration) {

  val applicationHomeFolder = new File(getApplicationHome)

  def getFolder(name: String) = {
    val folder = new File(applicationHomeFolder, name)
    if (!folder.exists()) {
      folder.mkdir()
      Logger.info(s"Created folder: ${folder.getAbsolutePath}")
    }
    folder
  }
  
  def moveFile(source: File, target: File) = {
    Files.copy(source.toPath, target.toPath)
    source.delete
  }
  
  def getFileExtension(file: File) = {
    file.getName.split("\\.").last
  }

  def getApplicationHome = {
    configuration.getString("application.home").filter(_.nonEmpty)
      .getOrElse(environment.rootPath.getPath)
  }
}
