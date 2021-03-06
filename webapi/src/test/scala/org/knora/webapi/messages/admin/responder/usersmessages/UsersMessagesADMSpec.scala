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

package org.knora.webapi.messages.admin.responder.usersmessages

import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataType, PermissionsDataADM}
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object UsersMessagesADMSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test subclasses of the [[UsersMessagesADM]] class.
  */
class UsersMessagesADMSpec extends CoreSpec(UsersMessagesADMSpec.config) {

    private val id = SharedTestDataADM.rootUser.id
    private val username = SharedTestDataADM.rootUser.username
    private val email = SharedTestDataADM.rootUser.email
    private val password = SharedTestDataADM.rootUser.password
    private val token = SharedTestDataADM.rootUser.token
    private val givenName = SharedTestDataADM.rootUser.givenName
    private val familyName = SharedTestDataADM.rootUser.familyName
    private val status = SharedTestDataADM.rootUser.status
    private val lang = SharedTestDataADM.rootUser.lang
    private val groups = SharedTestDataADM.rootUser.groups
    private val projects = SharedTestDataADM.rootUser.projects
    private val sessionId = SharedTestDataADM.rootUser.sessionId
    private val permissions = SharedTestDataADM.rootUser.permissions

    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    "The UserADM case class" should {
        "return a RESTRICTED UserADM when requested " in {
            val rootUser = UserADM(id = id, username = username, email = email, password = password, token = token, givenName = givenName, familyName = familyName, status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = permissions)
            val rootUserRestricted = UserADM(id = id, username = username, email = email, password = None, token = None, givenName = givenName, familyName = familyName, status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = permissions.ofType(PermissionDataType.RESTRICTED))

            assert(rootUser.ofType(UserInformationTypeADM.RESTRICTED) === rootUserRestricted)
        }

        "return true if user is ProjectAdmin in any project " in {
            assert(SharedTestDataADM.anythingAdminUser.permissions.isProjectAdminInAnyProject() === true, "user is not ProjectAdmin in any of his projects")
        }

        "return false if user is not ProjectAdmin in any project " in {
            assert(SharedTestDataADM.anythingUser1.permissions.isProjectAdminInAnyProject() === false, "user is ProjectAdmin in one of his projects")
        }

        "allow checking the SCrypt passwords" in {
            val encoder = new SCryptPasswordEncoder()
            val hp = encoder.encode("123456")
            val up = UserADM(id = "something", username = "something" , email = "something", password = Some(hp), token = None, givenName = "something", familyName = "something", status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = PermissionsDataADM())

            // test SCrypt
            assert(encoder.matches("123456", encoder.encode("123456")))

            // test UserADM SCrypt usage
            assert(up.passwordMatch("123456"))
        }

        "allow checking the BCrypt passwords" in {
            val encoder = new BCryptPasswordEncoder()
            val hp = encoder.encode("123456")
            val up = UserADM(id = "something", username = "something" , email = "something", password = Some(hp), token = None, givenName = "something", familyName = "something", status = status, lang = lang, groups = groups, projects = projects, sessionId = sessionId, permissions = PermissionsDataADM())

            // test BCrypt
            assert(encoder.matches("123456", encoder.encode("123456")))

            // test UserADM BCrypt usage
            assert(up.passwordMatch("123456"))
        }

        "allow checking the password (2)" in {
            SharedTestDataADM.rootUser.passwordMatch("test") should equal(true)
        }

        "return isSelf for IRI" in {
            SharedTestDataADM.rootUser.isSelf(UserIdentifierADM(maybeIri = Some(SharedTestDataADM.rootUser.id)))
        }

        "return isSelf for email" in {
            SharedTestDataADM.rootUser.isSelf(UserIdentifierADM(maybeEmail = Some(SharedTestDataADM.rootUser.email)))
        }

        "return isSelf for username" in {
            SharedTestDataADM.rootUser.isSelf(UserIdentifierADM(maybeUsername = Some(SharedTestDataADM.rootUser.username)))
        }
    }

    "The CreateUserApiRequestADM case class" should {

        "throw 'BadRequestException' if 'username'is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "",
                    email = "ddd@example.com",
                    givenName = "Donald",
                    familyName = "Duck",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'email' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "ddd",
                    email = "",
                    givenName = "Donald",
                    familyName = "Duck",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'password' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "donald.duck",
                    email = "donald.duck@example.com",
                    givenName = "Donald",
                    familyName = "Duck",
                    password = "",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'givenName' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "donald.duck",
                    email = "donald.duck@example.com",
                    givenName = "",
                    familyName = "Duck",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "throw 'BadRequestException' if 'familyName' is missing" in {

            assertThrows[BadRequestException](
                CreateUserApiRequestADM(
                    username = "donald.duck",
                    email = "donald.duck@example.com",
                    givenName = "Donald",
                    familyName = "",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
        }

        "return 'BadRequest' if the supplied 'id' is not a valid IRI" in {

            val caught = intercept[BadRequestException](
                CreateUserApiRequestADM(
                    id = Some("invalid-user-IRI"),
                    username = "userWithInvalidCustomIri",
                    email = "userWithInvalidCustomIri@example.org",
                    givenName = "a user",
                    familyName = "with an invalid custom Iri",
                    password = "test",
                    status = true,
                    lang = "en",
                    systemAdmin = false
                )
            )
            assert(caught.getMessage === "Invalid user IRI")
        }

    }

    "The UserIdentifierADM case class" should {

        "return the identifier type" in {

            val iriIdentifier = UserIdentifierADM(maybeIri = Some("http://rdfh.ch/users/root"))
            iriIdentifier.hasType should be (UserIdentifierType.IRI)

            val emailIdentifier = UserIdentifierADM(maybeEmail = Some("root@example.com"))
            emailIdentifier.hasType should be (UserIdentifierType.EMAIL)

            val usernameIdentifier = UserIdentifierADM(maybeUsername = Some("root"))
            usernameIdentifier.hasType should be (UserIdentifierType.USERNAME)
        }

        "check whether a user identified by email is the same as a user identified by username" in {
            val userEmail = "user@example.org"
            val username = "user"

            val user = UserADM(
                id = "http://rdfh.ch/users/example",
                username = username,
                email = userEmail,
                givenName = "Foo",
                familyName = "Bar",
                status = true,
                lang = "en"
            )

            val emailID = UserIdentifierADM(maybeEmail = Some(userEmail))
            val usernameID = UserIdentifierADM(maybeUsername = Some(username))

            assert(user.isSelf(emailID))
            assert(user.isSelf(usernameID))
        }

        "throw a BadRequestException for an empty identifier" in {
            assertThrows[BadRequestException](
                UserIdentifierADM()
            )
        }

        "throw a BadRequestException for an invalid user IRI" in {
            assertThrows[BadRequestException](
                UserIdentifierADM(maybeIri = Some("http://example.org/not/our/user/iri/structure"))
            )
        }

        "throw a BadRequestException for an invalid email" in {
            assertThrows[BadRequestException](
                UserIdentifierADM(maybeEmail = Some("invalidemail"))
            )
        }

        "throw a BadRequestException for an invalid username" in {
            assertThrows[BadRequestException](
                // we allow max 50 characters in username
                UserIdentifierADM(maybeEmail = Some("_username"))
            )
        }


    }
}
