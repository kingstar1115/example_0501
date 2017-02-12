package services.internal.services

import com.google.inject.ImplementedBy
import models.Tables.ServicesRow

@ImplementedBy(classOf[DefaultServicesService])
trait ServicesService {

  def hasInteriorCleaning(service: ServicesRow): Boolean
}
