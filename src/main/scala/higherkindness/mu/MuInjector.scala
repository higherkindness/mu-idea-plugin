/*
 * Copyright 2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package higherkindness.mu

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

object MuInjector {
  private val Log = Logger.getInstance(classOf[MuInjector])

  private val ServiceAnnotation = "service"

  private val Dummy: String = "_root_.scala.Predef.???"
  private def CE(effect: String): String = s"CE: _root_.cats.effect.ConcurrentEffect[$effect]"
  private def algebra(clazz: String, effect: String): String = s"algebra: $clazz[$effect]"
  private def SvcDef(effect: String): String = s"$effect[_root_.io.grpc.ServerServiceDefinition]"

  private def hasServiceAnnotation(source: ScTypeDefinition): Boolean = {
    source match {
      case t: ScTrait => t.annotations.exists(_.getText.contains(ServiceAnnotation))
      case _ => false
    }
  }
}

final class MuInjector extends SyntheticMembersInjector {
  import MuInjector._

  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    hasServiceAnnotation(source)

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScTypeDefinition if hasServiceAnnotation(clazz) => {
            Log.info(s"Injecting bindService into ${clazz.getName}")

            // TODO retrieve effects from clazz.typeParameters?
            val effect = "F"

            val companion =
              s"""|def bindService[$effect[_]](implicit ${CE(effect)},
                  |${algebra(clazz.getName, effect)}):
                  |${SvcDef(effect)} = $Dummy""".stripMargin
            List(companion)
          }
          case _ => Nil
        }
      case _ => Nil
    }
  }

}
