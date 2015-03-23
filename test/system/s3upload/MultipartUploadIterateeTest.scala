package system.s3upload

import fly.play.s3.{BucketFilePart, BucketFilePartUploadTicket}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.iteratee.{Step, Cont, Input}
import system.s3upload.S3UploadIteratee.IterateeType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class MultipartUploadIterateeTest extends FlatSpec with Matchers {
  "handleEmpty" should "return a Cont that captures the unmodified state" in {
    val sut = new MultipartUploadIteratee(new UnusedUploader, 0)
    val startingState = PartState.start

    def mockStep(state: PartState)(input: Input[Array[Byte]]): IterateeType = {
      state shouldBe startingState
      Cont[Array[Byte], Unit](i => ???)
    }

    val result = sut.handleEmpty(mockStep, startingState)
    result shouldBe a[Step.Cont[_, _]]
    Await.result(result.feed(Input.EOF), 10 seconds)
  }

  "handleEl" should "accumulate the bytes in a Cont when the total size is below the upload threshold" in {
    val sut = new MultipartUploadIteratee(new UnusedUploader, 100)
    val startingState = PartState.start
    val testBytes = "foo".getBytes
    val expectedState = startingState.addBytes(testBytes)

    def mockStep(state: PartState)(input: Input[Array[Byte]]): IterateeType = {
      state shouldEqual expectedState
      Cont[Array[Byte], Unit](i => ???)
    }

    val result = sut.handleEl(mockStep, startingState, testBytes)
    result shouldBe a[Step.Cont[_, _]]
    Await.result(result.feed(Input.EOF), 10 seconds)
  }

  "handleEl" should "upload the bytes the total size reaches the upload threshold" in {
    val mockUploader: MockUploader = new MockUploader
    val sut = new MultipartUploadIteratee(mockUploader, 3)
    val startingState = PartState.start
    val testBytes = "foo".getBytes
    val expectedState = startingState.addBytes(testBytes).nextPart(List(BucketFilePartUploadTicket(1, "")))

    def mockStep(state: PartState)(input: Input[Array[Byte]]): IterateeType = {
      state shouldBe expectedState
      Cont[Array[Byte], Unit](i => ???)
    }

    val result = sut.handleEl(mockStep, startingState, testBytes)
    result shouldBe a[Step.Cont[_, _]]
    mockUploader.uploadCount shouldBe 1
    Await.result(result.feed(Input.EOF), 10 seconds)
  }
}

/**
 * A dummy implementation that will throw an exception if any of its methods are called. Useful to test that no
 * upload operations happened during the test
 */
class UnusedUploader extends MultipartUploader {
  override def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket] = ???

  override def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[MultipartUploadResult] = ???

  override def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit] = ???
}

class MockUploader extends MultipartUploader {
  var uploadCount = 0

  override def uploadPart(part: BucketFilePart)(implicit ec: ExecutionContext): Future[BucketFilePartUploadTicket] = {
    uploadCount = uploadCount + 1
    Future(BucketFilePartUploadTicket(1, ""))
  }

  var completeCount = 0

  override def completeMultipartUpload(partUploadTickets: Seq[BucketFilePartUploadTicket])(implicit ec: ExecutionContext): Future[MultipartUploadResult] = {
    completeCount = completeCount + 1
    Future(MultipartUploadSuccess)
  }

  var abortCount = 0

  override def abortMultipartUpload(implicit ec: ExecutionContext): Future[Unit] = {
    abortCount = abortCount + 1
    Future(Unit)
  }
}