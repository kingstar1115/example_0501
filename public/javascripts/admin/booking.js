function getDaySlots(date) {
    $.ajax(jsRoutes.controllers.admin.BookingSlotController.getPartialBookingSlots(date))
        .done(function (data) {
            $('.bookingSlots').html(data);
        })
}
