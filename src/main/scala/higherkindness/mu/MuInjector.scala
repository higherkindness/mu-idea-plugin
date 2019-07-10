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
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector

object MuInjector {
  private val Log = Logger.getInstance(classOf[MuInjector])

  private val ServiceAnnotation = "service"

  private val Dummy: String = "_root_.scala.Predef.???"
  private val Client: String = "Client"
  private def CE(effect: String): String = s"CE: _root_.cats.effect.ConcurrentEffect[$effect]"
  private def CS(effect: String): String = s"CS: _root_.cats.effect.ContextShift[$effect]"
  private def algebra(serviceName: String, effect: String): String = s"algebra: $serviceName[$effect]"
  private def SvcDef(effect: String): String = s"$effect[_root_.io.grpc.ServerServiceDefinition]"
  private val DefaultGCall: String = "_root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT"
  private val Channel: String = "_root_.io.grpc.Channel"
  private val ChannelFor: String = "_root_.higherkindness.mu.rpc.ChannelFor"
  private val ManagedChannelConf: String = "_root_.higherkindness.mu.rpc.channel.ManagedChannelConfig"
  private def ClientStub(effect: String): String = s"_root_.io.grpc.stub.AbstractStub[$Client[$effect]](channel, options)"
  private val GCallOptions: String = "_root_.io.grpc.CallOptions"
  private val UsePlainText: String = "_root_.higherkindness.mu.rpc.channel.UsePlaintext()"
  private def ResourceForService(serviceName: String, effect: String): String =
    s"_root_.cats.effect.Resource[$effect, $serviceName[$effect]]"
  private val ManagedChannel: String = "_root_.io.grpc.ManagedChannel"

  // TODO conditional monix implicit required
  private def buildBindService(serviceName: String, effect: String): String =
    s"""|def bindService[$effect[_]](implicit ${CE(effect)},
        |${algebra(serviceName, effect)}):
        |${SvcDef(effect)} = $Dummy""".stripMargin

  private def buildClientClass(serviceName: String, effect: String, services: Seq[ScFunction]): String =
    s"""|class $Client[$effect[_]](
        |  channel: $Channel,
        |  options: $DefaultGCall
        |)(implicit ${CE(effect)}, ${CS(effect)}) extends ${ClientStub(effect)} with $serviceName[$effect] {
        |  override def build(
        |    channel: $Channel,
        |    options: $GCallOptions
        |  ): $Client[$effect] = new $Client[$effect](channel, options)
        |
        |  ${services.map(f => s"override ${f.getText} = $Dummy").mkString("\n")}
        |}""".stripMargin

  private def buildClientSmartConstructor(serviceName: String, effect: String): String =
    s"""|def client[$effect[_]](
        |  channelFor: $ChannelFor,
        |  channelConfigList: List[$ManagedChannelConf] = List(
        |    $UsePlainText
        |  ),
        |  options: $DefaultGCall
        |)(implicit ${CE(effect)}, ${CS(effect)}): ${ResourceForService(serviceName, effect)} =
        | $Dummy""".stripMargin

  private def buildClientFromChannel(serviceName: String, effect: String): String =
    s"""|def clientFromChannel[$effect[_]](
        |  channel: $effect[$ManagedChannel],
        |  options: $DefaultGCall
        |)(implicit ${CE(effect)}, ${CS(effect)}): ${ResourceForService(serviceName, effect)} =
        | $Dummy""".stripMargin

  private def hasServiceAnnotation(source: ScTypeDefinition): Boolean = {
    source match {
      case t: ScTrait => t.annotations.exists(_.getText.contains(ServiceAnnotation))
      case _ => false
    }
  }

  private def injector(scObject: ScObject)(generator: ScTypeDefinition => List[String]): List[String] = {
    case obj: ScObject =>
      obj.fakeCompanionClassOrCompanionClass match {
        case clazz: ScTypeDefinition if hasServiceAnnotation(clazz) => generator(clazz)
        case _ => Nil
      }
  }
}

final class MuInjector extends SyntheticMembersInjector {
  import MuInjector._

  override def needsCompanionObject(source: ScTypeDefinition): Boolean =
    hasServiceAnnotation(source)

  override def injectInners(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject => injector(obj) { clazz =>
      // TODO retrieve effects from clazz.typeParameters?
      val effect = "F"
      val serviceName = clazz.getName

      List(buildClientClass(serviceName, effect, clazz.functions))
    }
    case _ => Nil
  }

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = source match {
    case obj: ScObject => injector(obj) { clazz =>
      // TODO retrieve effects from clazz.typeParameters?
      val effect = "F"
      val serviceName = clazz.getName

      val bind = buildBindService(serviceName, effect)
      val clientConstructor = buildClientSmartConstructor(serviceName, effect)
      val clientFromChannel = buildClientFromChannel(serviceName, effect)

      List(bind, clientConstructor, clientFromChannel)
    }
    case _ => Nil
  }

}
