package dto.rest.v4

import controllers.rest.TasksController.{CustomerPaymentDetails, ServiceDto}
import play.api.libs.json.{Json, Reads}

object Task {

  case class CustomerTaskDto(description: String, address: String, latitude: Double, longitude: Double,
                             timeSlotId: Int, vehicleId: Int, service: ServiceDto,
                             paymentDetails: CustomerPaymentDetails)

  object CustomerTaskDto {
    implicit val jsonReads: Reads[CustomerTaskDto] = Json.reads[CustomerTaskDto]
  }

}
