package services

import java.nio.file.Files
import javax.inject.{Inject, Singleton}

import play.api.{Logger, Configuration}
import java.io.File

@Singleton
class FileService @Inject()(application: play.Application,
                            configuration: Configuration) {

  val applicationHomeFolder = new File(getApplicationHome)

  def getFolder(name: String) = {
    val folder = new File(applicationHomeFolder, name)
    if (!folder.exists()){
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
      .getOrElse(application.path().getPath)
  }
}
