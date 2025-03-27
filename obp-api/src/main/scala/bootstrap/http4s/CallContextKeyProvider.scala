package bootstrap.http4s

import cats.effect.IO
import org.typelevel.vault.Key
import code.api.util.CallContext

object CallContextKeyProvider {
  
  import cats.effect.unsafe.implicits.global
  
  // Centralized, safely initialized Vault Key for CallContext
  val callContextKey: Key[CallContext] = createKey()

  private def createKey(): Key[CallContext] = {
    // If this fails, it fails early and visibly at app startup
    Key.newKey[IO, CallContext].unsafeRunSync()
  }

}
