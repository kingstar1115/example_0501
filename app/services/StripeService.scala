package services

import java.util
import javax.inject.{Inject, Singleton}

import com.stripe.Stripe
import com.stripe.model.Charge
import play.api.Configuration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class StripeService @Inject()(configuration: Configuration) {

  Stripe.apiKey = configuration.getString("stripe.key").get

  def charge(amount: Int, source: String) = {
    val params = new util.HashMap[String, Object]() {
      put("amount", new Integer(amount))
      put("currency", "usd")
      put("source", source)
    }
    Future(Charge.create(params))
  }
}
