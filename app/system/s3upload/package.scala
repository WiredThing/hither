package system

import fly.play.s3.BucketFilePartUploadTicket

import scala.collection.mutable.ArraySeq
import scala.concurrent.Future

package object s3upload {

  case class PartState(partNumber: PartNumber, totalSize: DataLength, accumulatedBytes: ArraySeq[Byte], uploadTickets: List[BucketFilePartUploadTicket]) {
    def addBytes(bytes: Array[Byte]): PartState = copy(accumulatedBytes = accumulatedBytes ++ bytes)

    def nextPart(tickets: List[BucketFilePartUploadTicket]): PartState = copy(partNumber = partNumber.inc, totalSize = totalSize.add(accumulatedBytes.length), ArraySeq(), uploadTickets = tickets)
  }

  object PartState {
    val start: PartState = PartState(PartNumber.one, DataLength.zero, ArraySeq(), List())
  }

  case class PartNumber(n: Int) extends AnyVal {
    def inc: PartNumber = PartNumber(n + 1)
  }

  object PartNumber {
    val one = PartNumber(1)
  }

  case class DataLength(l: Int) extends AnyVal {
    def add(a: Int) = DataLength(l + a)
  }

  object DataLength {
    val zero = DataLength(0)
  }

}
