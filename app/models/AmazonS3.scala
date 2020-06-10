package models

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.transfer.TransferManager

trait AmazonS3 {
  val bucketName = "plagiarism-detector-file-storage"
  val AWS_ACCESS_KEY = "AKIAILLURPTBNJPK6NAQ"
  val AWS_SECRET_KEY = "4Mto9pfOQzhD2ttwIgtghCgxE0T87gyrca1V+qMj"
  val yourAWSCredentials = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
  val amazonS3Client = new AmazonS3Client(yourAWSCredentials)
}
