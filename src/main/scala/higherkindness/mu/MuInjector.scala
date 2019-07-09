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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

final class MuInjector extends SyntheticMembersInjector {
  private val Log = Logger.getInstance(classOf[MuInjector])

  override def injectInners(source: ScTypeDefinition): Seq[String] = {
    source.extendsBlock.members.flatMap {
      case c: ScClass if c.annotations.map(_.getText).contains("service") =>
        Log.info(s"Checking ${c.getQualifiedName}")
        val companion =
          s"""
             |object ${c.name} {
             |  def bindService[F[_]](implicit CE: _root_.cats.effect.ConcurrentEffect[F], algebra: ${c.name}[F]): F[_root_.io.grpc.ServerServiceDefinition]
             |}
           """.stripMargin

        Seq(companion)
    }
  }

}
