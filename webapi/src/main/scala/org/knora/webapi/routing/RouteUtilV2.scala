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

package org.knora.webapi.routing

import java.io.{StringReader, StringWriter}

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.pattern._
import akka.util.Timeout
import org.eclipse.rdf4j.rio._
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings
import org.eclipse.rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter
import org.knora.webapi._
import org.knora.webapi.exceptions.{AssertionException, BadRequestException, UnexpectedMessageException}
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.util.JsonLDDocument
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourceTEIGetResponseV2
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import org.knora.webapi.messages.{SmartIri, StringFormatter}
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.Exception.catching

/**
 * Handles message formatting, content negotiation, and simple interactions with responders, on behalf of Knora routes.
 */
object RouteUtilV2 {
    /**
     * The name of the HTTP header in which an ontology schema can be requested.
     */
    val SCHEMA_HEADER: String = "x-knora-accept-schema"

    /**
     * The name of the URL parameter in which an ontology schema can be requested.
     */
    val SCHEMA_PARAM: String = "schema"

    /**
     * The name of the complex schema.
     */
    val SIMPLE_SCHEMA_NAME: String = "simple"

    /**
     * The name of the simple schema.
     */
    val COMPLEX_SCHEMA_NAME: String = "complex"

    /**
     * The name of the HTTP header in which results from a project can be requested.
     */
    val PROJECT_HEADER: String = "x-knora-accept-project"

    /**
     * The name of the URL parameter that can be used to specify how markup should be returned
     * with text values.
     */
    val MARKUP_PARAM: String = "markup"

    /**
     * The name of the HTTP header that can be used to specify how markup should be returned with
     * text values.
     */
    val MARKUP_HEADER: String = "x-knora-accept-markup"

    /**
     * Indicates that standoff markup should be returned as XML with text values.
     */
    val MARKUP_XML: String = "xml"

    /**
     * Indicates that markup should not be returned with text values, because it will be requested
     * separately as standoff.
     */
    val MARKUP_STANDOFF: String = "standoff"

    /**
     * Gets the ontology schema that is specified in an HTTP request. The schema can be specified
     * either in the HTTP header [[SCHEMA_HEADER]] or in the URL parameter [[SCHEMA_PARAM]].
     * If no schema is specified in the request, the default of [[ApiV2Complex]] is returned.
     *
     * @param requestContext the akka-http [[RequestContext]].
     * @return the specified schema, or [[ApiV2Complex]] if no schema was specified in the request.
     */
    def getOntologySchema(requestContext: RequestContext): ApiV2Schema = {
        def nameToSchema(schemaName: String): ApiV2Schema = {
            schemaName match {
                case SIMPLE_SCHEMA_NAME => ApiV2Simple
                case COMPLEX_SCHEMA_NAME => ApiV2Complex
                case _ => throw BadRequestException(s"Unrecognised ontology schema name: $schemaName")
            }
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap

        params.get(SCHEMA_PARAM) match {
            case Some(schemaParam) => nameToSchema(schemaParam)

            case None =>
                requestContext.request.headers.find(_.lowercaseName == SCHEMA_HEADER) match {
                    case Some(header) => nameToSchema(header.value)
                    case None => ApiV2Complex
                }
        }
    }

    /**
     * Gets the type of standoff rendering that should be used when returning text with standoff.
     * The name of the standoff rendering can be specified either in the HTTP header [[MARKUP_HEADER]]
     * or in the URL parameter [[MARKUP_PARAM]]. If no rendering is specified in the request, the
     * default of [[MarkupAsXml]] is returned.
     *
     * @param requestContext the akka-http [[RequestContext]].
     * @return the specified standoff rendering, or [[MarkupAsXml]] if no rendering was specified
     *         in the request.
     */
    private def getStandoffRendering(requestContext: RequestContext): Option[MarkupRendering] = {
        def nameToStandoffRendering(standoffRenderingName: String): MarkupRendering = {
            standoffRenderingName match {
                case MARKUP_XML => MarkupAsXml
                case MARKUP_STANDOFF => MarkupAsStandoff
                case _ => throw BadRequestException(s"Unrecognised standoff rendering: $standoffRenderingName")
            }
        }

        val params: Map[String, String] = requestContext.request.uri.query().toMap

        params.get(MARKUP_PARAM) match {
            case Some(schemaParam) => Some(nameToStandoffRendering(schemaParam))

            case None =>
                requestContext.request.headers.find(_.lowercaseName == MARKUP_HEADER).map {
                    header => nameToStandoffRendering(header.value)
                }
        }
    }

    /**
     * Gets the schema options submitted in the request.
     *
     * @param requestContext the request context.
     * @return the set of schema options submitted in the request, including default options.
     */
    def getSchemaOptions(requestContext: RequestContext): Set[SchemaOption] = {
        getStandoffRendering(requestContext).toSet
    }

    /**
     * Gets the project IRI specified in a Knora-specific HTTP header.
     *
     * @param requestContext the akka-http [[RequestContext]].
     * @return the specified project IRI, or [[None]] if no project header was included in the request.
     */
    def getProject(requestContext: RequestContext)(implicit stringFormatter: StringFormatter): Option[SmartIri] = {
        requestContext.request.headers.find(_.lowercaseName == PROJECT_HEADER).map {
            header =>
                val projectIriStr = header.value
                projectIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $projectIriStr"))
        }
    }

    /**
     * Sends a message to a responder and completes the HTTP request by returning the response as RDF using content negotation.
     *
     * @param requestMessage   a future containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
     * @param requestContext   the akka-http [[RequestContext]].
     * @param settings         the application's settings.
     * @param responderManager a reference to the responder manager.
     * @param log              a logging adapter.
     * @param targetSchema     the API schema that should be used in the response.
     * @param timeout          a timeout for `ask` messages.
     * @param executionContext an execution context for futures.
     * @return a [[Future]] containing a [[RouteResult]].
     */
    private def runRdfRoute(requestMessage: KnoraRequestV2,
                            requestContext: RequestContext,
                            settings: KnoraSettingsImpl,
                            responderManager: ActorRef,
                            log: LoggingAdapter,
                            targetSchema: ApiV2Schema,
                            schemaOptions: Set[SchemaOption])
                           (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        // Optionally log the request message. TODO: move this to the testing framework.
        if (settings.dumpMessages) {
            log.debug(requestMessage.toString)
        }

        val httpResponse: Future[HttpResponse] = for {
            // Make sure the responder sent a reply of type KnoraResponseV2.
            knoraResponse <- (responderManager ? requestMessage).map {
                case replyMessage: KnoraResponseV2 => replyMessage

                case other =>
                    // The responder returned an unexpected message type (not an exception). This isn't the client's
                    // fault, so log it and return an error message to the client.
                    throw UnexpectedMessageException(s"Responder sent a reply of type ${other.getClass.getCanonicalName}")
            }

            // Optionally log the reply message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(knoraResponse.toString)
            }

            // Choose a media type for the response.
            responseMediaType: MediaType.NonBinary = chooseRdfMediaTypeForResponse(requestContext)

            // Format the response message as an HTTP response.
            formattedResponse: HttpResponse = formatResponse(
                knoraResponse = knoraResponse,
                responseMediaType = responseMediaType,
                targetSchema = targetSchema,
                settings = settings,
                schemaOptions = schemaOptions
            )

            // The request was successful
        } yield formattedResponse

        requestContext.complete(httpResponse)
    }

    /**
     * Sends a message to a responder and completes the HTTP request by returning the response as TEI/XML.
     *
     * @param requestMessageF  a future containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
     * @param requestContext   the akka-http [[RequestContext]].
     * @param settings         the application's settings.
     * @param responderManager a reference to the responder manager.
     * @param log              a logging adapter.
     * @param targetSchema     the API schema that should be used in the response.
     * @param timeout          a timeout for `ask` messages.
     * @param executionContext an execution context for futures.
     * @return a [[Future]] containing a [[RouteResult]].
     */
    def runTEIXMLRoute(requestMessageF: Future[KnoraRequestV2],
                       requestContext: RequestContext,
                       settings: KnoraSettingsImpl,
                       responderManager: ActorRef,
                       log: LoggingAdapter,
                       targetSchema: ApiV2Schema)
                      (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

        val contentType = MediaTypes.`application/xml`.toContentType(HttpCharsets.`UTF-8`)

        val httpResponse: Future[HttpResponse] = for {

            requestMessage <- requestMessageF

            teiResponse <- (responderManager ? requestMessage).map {
                case replyMessage: ResourceTEIGetResponseV2 => replyMessage

                case other =>
                    // The responder returned an unexpected message type (not an exception). This isn't the client's
                    // fault, so log it and return an error message to the client.
                    throw UnexpectedMessageException(s"Responder sent a reply of type ${other.getClass.getCanonicalName}")
            }


        } yield HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                contentType,
                teiResponse.toXML
            )
        )

        requestContext.complete(httpResponse)
    }

    /**
     * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as RDF.
     *
     * @param requestMessageF  a [[Future]] containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
     * @param requestContext   the akka-http [[RequestContext]].
     * @param settings         the application's settings.
     * @param responderManager a reference to the responder manager.
     * @param log              a logging adapter.
     * @param targetSchema     the API schema that should be used in the response.
     * @param schemaOptions    the schema options that should be used when processing the request.
     * @param timeout          a timeout for `ask` messages.
     * @param executionContext an execution context for futures.
     * @return a [[Future]] containing a [[RouteResult]].
     */
    def runRdfRouteWithFuture(requestMessageF: Future[KnoraRequestV2],
                              requestContext: RequestContext,
                              settings: KnoraSettingsImpl,
                              responderManager: ActorRef,
                              log: LoggingAdapter,
                              targetSchema: ApiV2Schema,
                              schemaOptions: Set[SchemaOption])
                             (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        for {
            requestMessage <- requestMessageF
            routeResult <- runRdfRoute(
                requestMessage = requestMessage,
                requestContext = requestContext,
                settings = settings,
                responderManager = responderManager,
                log = log,
                targetSchema = targetSchema,
                schemaOptions = schemaOptions
            )

        } yield routeResult
    }

    /**
     * Chooses an RDF media type for the response, using content negotiation as per [[https://tools.ietf.org/html/rfc7231#section-5.3.2]].
     *
     * @param requestContext the request context.
     * @return an RDF media type.
     */
    private def chooseRdfMediaTypeForResponse(requestContext: RequestContext): MediaType.NonBinary = {
        // Get the client's HTTP Accept header, if provided.
        val maybeAcceptHeader: Option[HttpHeader] = requestContext.request.headers.find(_.lowercaseName == "accept")

        maybeAcceptHeader match {
            case Some(acceptHeader) =>
                // Parse the value of the accept header, filtering out non-RDF media types, and sort the results
                // in reverse order by q value.
                val acceptMediaTypes: Array[MediaType.NonBinary] = acceptHeader.value.split(',').flatMap {
                    headerValueItem =>
                        val mediaRangeParts: Array[String] = headerValueItem.split(';').map(_.trim)
                        val mediaTypeStr: String = mediaRangeParts.headOption.getOrElse(throw BadRequestException(s"Invalid Accept header: ${acceptHeader.value}"))

                        // Get the qValue, if provided; it defaults to 1.
                        val qValue: Float = mediaRangeParts.tail.flatMap {
                            param =>
                                param.split('=').map(_.trim) match {
                                    case Array("q", qValueStr) => catching(classOf[NumberFormatException]).opt(qValueStr.toFloat)
                                    case _ => None // Ignore other parameters.
                                }
                        }.headOption.getOrElse(1)

                        val maybeMediaType: Option[MediaType] = RdfMediaTypes.registry.get(mediaTypeStr) match {
                            case Some(mediaType: MediaType) => Some(mediaType)
                            case _ => None // Ignore non-RDF media types.
                        }

                        maybeMediaType.map(mediaType => MediaRange.One(mediaType, qValue))
                }.sortBy(_.qValue).reverse.map(_.mediaType).collect {
                    // All RDF media types are non-binary.
                    case nonBinary: MediaType.NonBinary => nonBinary
                }

                // Select the highest-ranked supported media type.
                acceptMediaTypes.headOption match {
                    case Some(requested) => requested
                    case None =>
                        // If there isn't one, use JSON-LD.
                        RdfMediaTypes.`application/ld+json`
                }

            case None => RdfMediaTypes.`application/ld+json`
        }
    }

    /**
     * Constructs an HTTP response containing a Knora response message formatted in the requested media type.
     *
     * @param knoraResponse     the response message.
     * @param responseMediaType the media type selected for the response (must be one of the types in [[RdfMediaTypes]]).
     * @param targetSchema      the response schema.
     * @param schemaOptions     the schema options.
     * @param settings          the application settings.
     * @return an HTTP response.
     */
    private def formatResponse(knoraResponse: KnoraResponseV2,
                               responseMediaType: MediaType.NonBinary,
                               targetSchema: ApiV2Schema,
                               schemaOptions: Set[SchemaOption],
                               settings: KnoraSettingsImpl): HttpResponse = {
        // Find the most specific media type that is compatible with the one requested.
        val specificMediaType = RdfMediaTypes.toMostSpecificMediaType(responseMediaType)

        // Convert the requested media type to a UTF-8 content type.
        val contentType = RdfMediaTypes.toUTF8ContentType(responseMediaType)

        // Generate a JSON-LD data structure from the API response message.
        val jsonLDDocument: JsonLDDocument = knoraResponse.toJsonLDDocument(
            targetSchema = targetSchema,
            settings = settings,
            schemaOptions = schemaOptions
        )

        // Is the response format JSON or JSON-LD?
        specificMediaType match {
            case RdfMediaTypes.`application/ld+json` =>
                // Yes. Pretty-print the JSON-LD and return it.
                HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                        contentType,
                        jsonLDDocument.toPrettyString
                    )
                )

            case _ =>
                // No, some other format was requested. Convert the JSON-LD to the requested format.

                val rdfParser: RDFParser = Rio.createParser(RDFFormat.JSONLD)
                val stringReader = new StringReader(jsonLDDocument.toCompactString)
                val stringWriter = new StringWriter()

                val rdfWriter: RDFWriter = specificMediaType match {
                    case RdfMediaTypes.`text/turtle` =>
                        val turtleWriter = Rio.createWriter(RDFFormat.TURTLE, stringWriter)
                        turtleWriter.getWriterConfig.set[java.lang.Boolean](BasicWriterSettings.INLINE_BLANK_NODES, true).
                            set[java.lang.Boolean](BasicWriterSettings.PRETTY_PRINT, true)
                        turtleWriter

                    case RdfMediaTypes.`application/rdf+xml` => new RDFXMLPrettyWriter(stringWriter)

                    case _ => throw AssertionException(s"Media type $responseMediaType not implemented")
                }

                rdfParser.setRDFHandler(rdfWriter)
                rdfParser.parse(stringReader, "")

                HttpResponse(
                    status = StatusCodes.OK,

                    entity = HttpEntity(
                        contentType,
                        stringWriter.toString
                    )
                )
        }
    }
}