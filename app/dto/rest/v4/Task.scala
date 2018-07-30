package dto.rest.v4

import controllers.rest.TasksController.{AnonymousPaymentDetails, CustomerPaymentDetails, ServiceDto, UserDto, VehicleDetailsDto}
import play.api.libs.json.{Json, Reads}

object Task {

  case class CustomerTaskDto(description: String, address: String, latitude: Double, longitude: Double,
                             timeSlotId: Int, vehicleId: Int, service: ServiceDto,
                             paymentDetails: CustomerPaymentDetails)

  object CustomerTaskDto {
    implicit val jsonReads: Reads[CustomerTaskDto] = Json.reads[CustomerTaskDto]
  }

  case class AnonymousTaskDto(description: String, address: String, latitude: Double,
                              longitude: Double, timeSlotId: Int, userDto: UserDto, vehicleDto: VehicleDetailsDto,
                              serviceDto: ServiceDto, paymentDetails: AnonymousPaymentDetails)

  object AnonymousTaskDto {
    implicit val jsonReads: Reads[AnonymousTaskDto] = Json.reads[AnonymousTaskDto]
  }

}
