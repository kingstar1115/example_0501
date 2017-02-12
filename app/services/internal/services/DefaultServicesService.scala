package services.internal.services

import models.Tables._

class DefaultServicesService extends ServicesService {

  val exteriorAndInteriorCleaning = "EXTERIOR_AND_INTERIOR_CLEANING"

  override def hasInteriorCleaning(service: ServicesRow): Boolean =
    service.key.exists(_.equals(exteriorAndInteriorCleaning))
}
