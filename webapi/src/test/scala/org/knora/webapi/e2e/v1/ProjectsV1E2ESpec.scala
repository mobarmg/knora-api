/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.knora.webapi.util.AkkaHttpUtils

import scala.concurrent.duration._


object ProjectsV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing groups endpoint.
  */
class ProjectsV1E2ESpec extends E2ESpec(ProjectsV1E2ESpec.config) with SessionJsonProtocol with ProjectV1JsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    private val rootEmail = SharedTestDataV1.rootUser.userData.email.get
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val projectIri = SharedTestDataV1.imagesProjectInfo.id
    private val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")
    private val projectShortName = SharedTestDataV1.imagesProjectInfo.shortname
    private val projectShortnameEnc = java.net.URLEncoder.encode(projectShortName, "utf-8")

    "The Projects Route ('v1/projects')" when {

        "used to query for project information" should {

            "return all projects" in {
                val request = Get(baseApiUrl + s"/v1/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // logger.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                // logger.debug("projects as objects: {}", AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]])

                val projects: Seq[ProjectInfoV1] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]]

                projects.size should be (8)

            }

            "return the information for a single project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // logger.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the information for a single project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // logger.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }
        }
    }
}
