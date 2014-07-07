package system.registry



import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import play.api.Logger
import play.api.libs.iteratee._

import fly.play.s3.{BucketFile, BucketFilePart, BucketFilePartUploadTicket, S3}

import services.ContentEnumerator
import system.Configuration
import models.ImageId

trait S3Registry extends PrivateRegistry {
  def bucketName: String

  def s3: S3

  implicit def app: play.api.Application

  override def findResource(imageId: ImageId, resourceType: ResourceType)(implicit ctx: ExecutionContext): Future[Option[ContentEnumerator]] = {
    val pathName = s"${Configuration.registryRoot}/${imageId.id}.${resourceType.name}"

    val url: String = httpUrl(bucketName, s3.host, pathName)

    val ws = s3.awsWithSigner.url(url).sign("GET")

    ws.withRequestTimeout((10 minutes).toMillis.toInt).getStream().map { result =>
      result match {
        case (headers, e) if headers.status == 200 =>
          val contentLength = for {
            ls <- headers.headers.get("Content-Length")
            s <- ls.headOption
            l <- Try(s.toLong).toOption
          } yield l
          Some(ContentEnumerator(e, resourceType.contentType, contentLength))
        case (headers, _) if headers.status == 403 => throw new Exception("Unauthorized")
        case _ => None
      }
    }
  }


  override def layerHead(imageId: ImageId)(implicit ctx: ExecutionContext): Future[Option[Long]] = {
    val bucket = s3.getBucket(bucketName)
    val fileName = s"${Configuration.registryRoot}/${imageId.id}.layer"
    val bucketFile = BucketFile(fileName, ResourceType.LayerType.contentType)

    Future {
      bucketFile.headers.flatMap { h =>
        h.get("Content-Length").flatMap(l => Try(l.toLong).toOption)
      }
    }
  }

  override def sinkFor(imageId: ImageId, resourceType: ResourceType, contentLength: Option[Long])(implicit ctx: ExecutionContext): Iteratee[Array[Byte], Unit] = {
    import scala.concurrent.Await

    val bucket = s3.getBucket(bucketName)
    val fileName = s"${Configuration.registryRoot}/${imageId.id}.${resourceType.name}"
    val bucketFile = BucketFile(fileName, resourceType.contentType)

    val fit = bucket.initiateMultipartUpload(bucketFile).map { ticket =>
      Logger.info(s"Multipart upload to ${bucketFile.name} started with ticket ${ticket}")
      contentLength match {
        case Some(l) => Logger.info(s"Expecting $l bytes of data")
        case None => Logger.info(s"Don't know how much data to expect")
      }

      def step(partNumber: Int, totalSize: Int, accumulatedChunks: Array[Byte], uploadTickets: Future[List[BucketFilePartUploadTicket]])(i: Input[Array[Byte]]): Iteratee[Array[Byte], Unit] = i match {
        case Input.EOF => {
          Logger.info(s"Got EOF as part number $partNumber. Total data size is ${totalSize + accumulatedChunks.length}")
          val f = uploadTickets.flatMap { ts =>
            Logger.info(s"Pushing final part $partNumber with ${accumulatedChunks.length} bytes")
            val uploadTicket = bucket.uploadPart(ticket, BucketFilePart(partNumber, accumulatedChunks))

            uploadTicket.map { t =>
              Logger.info(s"Completing upload with $t and tickets for ${ts.size + 1} parts")
              bucket.s3.completeMultipartUpload(bucket.name, ticket, (t +: ts).reverse).onComplete {
                case Success(response) => response.status match {
                  case 200 => Logger.info(s"Multipart upload response was ${response.status}")
                  case x => Logger.info(s"Response to multipart upload completion was $x (${response.body})")
                }
                case Failure(ex) => Logger.info("Multipart upload failed", ex)
              }
            }
          }
          Await.result(f, 10 minutes)
          Done(0, Input.EOF)
        }

        case Input.El(bytes) =>
          val chunk = accumulatedChunks ++ bytes
          if (chunk.length >= 5 * 1024 * 1024) {
            Logger.info(s"Got Input for part $partNumber with ${chunk.length} bytes of data. Total so far is ${totalSize + chunk.length}")

            val f = uploadTickets.flatMap { ts =>
              Logger.info(s"Pushing part $partNumber with ${chunk.length} bytes. $totalSize bytes uploaded so far")
              bucket.uploadPart(ticket, BucketFilePart(partNumber, chunk)).map { t =>
                t +: ts
              }
            }

            Await.result(f, 10 minutes)

            Cont[Array[Byte], Unit](i => step(partNumber + 1, totalSize + chunk.length, Array(), f)(i))
          } else {
            Cont[Array[Byte], Unit](i => step(partNumber, totalSize, chunk, uploadTickets)(i))
          }

        case Input.Empty => Cont[Array[Byte], Unit](i => step(partNumber, totalSize, accumulatedChunks, uploadTickets)(i))
      }

      Cont[Array[Byte], Unit](i => step(1, 0, Array(), Future(List()))(i))
    }

    Iteratee.flatten(fit)
  }

  def httpUrl(bucketName: String, host: String, path: String) = {
    val protocol = "http"
    protocol + "://" + bucketName + "." + host + "/" + path
  }
}
