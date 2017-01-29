package services.internal.accommodations

import com.google.inject.ImplementedBy
import models.Tables.AccommodationsRow

@ImplementedBy(classOf[DefaultAccommodationService])
trait AccommodationsService {

  def hasInteriorCleaning(accommodation: AccommodationsRow): Boolean
}
