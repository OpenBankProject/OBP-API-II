package code.api.http4s

import cats.effect.IO
import com.openbankproject.commons.model._
import code.api.util.CallContext
import code.model.dataAccess.AuthUser
import net.liftweb.common.Full
import code.api.util.APIUtil._
import code.api.util.CallContext
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Full}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scala.util.{Failure, Success}

object AuthZChecks {

  // Convert Future to IO with proper exception handling
  private def futureToIO[T](f: => Future[T]): IO[T] = {
    IO.async_ { cb =>
      f.onComplete {
        case Success(value) => cb(Right(value))
        case Failure(ex)    => cb(Left(ex))
      }(global)
    }
  }

  def checkAuth(req: org.http4s.Request[cats.effect.IO],cc: CallContext): IO[(Option[User], CallContext)] = {
    val resultF =
      //TODO  here should be `isNeedCheckAuth`,we should use resourceDos here.
//      if (isNeedCheckAuth) authenticatedAccessFun(cc)
//      else anonymousAccessFun(cc) 
      authenticatedAccessFunHttp4s(req,cc)

    futureToIO(resultF).map {
      case (Full(user), Some(updatedCtx)) => (Some(user), updatedCtx)
      case (_, Some(updatedCtx))          => (None, updatedCtx)
      case _                              => (None, cc)
    }
  }

//  def checkRoles(bankId: Option[BankId], userOpt: Option[User], ctx: CallContext): IO[Unit] = IO {
//    userOpt match {
//      case Some(user) =>
//        // TODO: Add real role checking logic here
//        ()
//      case None =>
//        throw new RuntimeException("Missing user during role check.")
//    }
//  }

//  def checkBank(bankIdOpt: Option[BankId], ctx: CallContext): IO[(Bank, CallContext)] = IO {
//    val bankId = bankIdOpt.getOrElse(throw new RuntimeException("Missing bankId"))
//    val bank = Bank(bankId.value) // Replace with real bank fetch
//    (bank, ctx.copy(bankId = Some(bankId)))
//  }
//
//  def checkAccount(
//    bankIdOpt: Option[BankId],
//    accountIdOpt: Option[AccountId],
//    ctx: CallContext
//  ): IO[(BankAccount, CallContext)] = IO {
//    val bankId = bankIdOpt.getOrElse(throw new RuntimeException("Missing bankId"))
//    val accountId = accountIdOpt.getOrElse(throw new RuntimeException("Missing accountId"))
//
//    val account = BankAccount(accountId.value, bankId.value) // Replace with real fetch logic
//    (account, ctx)
//  }

//  def checkView(
//    viewIdOpt: Option[ViewId],
//    bankId: Option[BankId],
//    accountId: Option[AccountId],
//    userOpt: Option[User],
//    ctx: CallContext
//  ): IO[View] = IO {
//    viewIdOpt match {
//      case Some(viewId) =>
//        // TODO: Integrate with view permission check
//        View(viewId.value)
//      case None =>
//        throw new RuntimeException("Missing viewId")
//    }
//  }

//  def checkCounterparty(counterpartyIdOpt: Option[CounterpartyId], ctx: CallContext): IO[Counterparty] = IO {
//    counterpartyIdOpt match {
//      case Some(counterpartyId) => Counterparty(counterpartyId.value)
//      case None => throw new RuntimeException("Missing counterpartyId")
//    }
//  }

} 
