package services.internal.accommodations


class DefaultAccommodationService extends AccommodationsService {

  val exteriorAndInteriorCleaning = "EXTERIOR_AND_INTERIOR_CLEANING"

  override def hasInteriorCleaning(accommodation: _root_.models.Tables.AccommodationsRow): Boolean =
    accommodation.key.exists(_.equals(exteriorAndInteriorCleaning))
}
