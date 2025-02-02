/*
 * Copyright 2021 Anton Sviridov
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

package razoryak

import semver4s.Version

sealed abstract class ScalaVersion(val raw: String)
    extends Product
    with Serializable {
  def previous: Option[ScalaVersion] = this match {
    case Scala211   => None
    case Scala212   => Some(Scala211)
    case Scala213   => Some(Scala212)
    case Scala3_M3  => Some(Scala213)
    case Scala3_RC1 => Some(Scala3_M3)
  }
}
case object Scala211 extends ScalaVersion("2.11")

case object Scala212   extends ScalaVersion("2.12")
case object Scala213   extends ScalaVersion("2.13")
case object Scala3_M3  extends ScalaVersion("3.0.0-M3")
case object Scala3_RC1 extends ScalaVersion("3.0.0-RC1")

object ScalaVersion {
  def unapply(s: String): Option[ScalaVersion] = s match {
    case "2.13"      => Option(Scala213)
    case "2.12"      => Option(Scala212)
    case "2.11"      => Option(Scala211)
    case "3.0.0-M3"  => Option(Scala3_M3)
    case "3.0.0-RC1" => Option(Scala3_RC1)
  }
}

sealed trait ScalaPlatform
case object JVM    extends ScalaPlatform
case object NATIVE extends ScalaPlatform
case object JS     extends ScalaPlatform

sealed trait StateOfThings
case class Exists(config: Config, version: String) extends StateOfThings
case class NeedsCreation(config: Config, dependencies: Seq[StateOfThings])
    extends StateOfThings

case class Axis(scalaVersion: ScalaVersion, platform: ScalaPlatform)

case class VersionedArtifact(artifact: Artifact, version: Version) {
  override def toString = artifact.completionArtifact + version.format
}

sealed trait Action extends Product with Serializable
case class PublishFor(
    artifact: Artifact
) extends Action
case class UpgradeDependency(
    artifact: Artifact,
    dependency: VersionedArtifact,
    from: Version
)                                                    extends Action
case class Use(artifact: Artifact, version: Version) extends Action

sealed trait Problem                          extends Throwable with Product with Serializable
case class ArtifactDoesntExist(art: Artifact) extends Problem
case class NoSuitableVersions(art: Artifact, options: List[Version])
    extends Problem

case class Plan(actions: Vector[Action])

case class Artifact(
    tests: Boolean,
    org: String,
    name: String,
    axis: Axis
) {

  def completionName = {
    var base = name
    if (axis.platform == JS) base += "_sjs1"
    else if (axis.platform == NATIVE) base += "_native0.4"

    base += "_" + axis.scalaVersion.raw

    base
  }

  def completionArtifact = {
    org + ":" + completionName + ":"
  }

  def alternatives: List[Artifact] = {
    axis.scalaVersion.previous match {
      case None => Nil
      case Some(sv) =>
        val nt = copy(axis = axis.copy(scalaVersion = sv))
        nt +: nt.alternatives
    }
  }
}

object Artifact {
  def fromConfig(c: Config) = Artifact(
    tests = c.tests,
    org = c.org,
    name = c.name,
    axis = Axis(c.scalaVersion, c.platform)
  )
}
