/*
Sample example spark job
Just reads the file, executes a query and writes back to an s3 bucket.
In a real world scenario, this job can be a batch processing job which can run for hours depending on the dataset.
This example job is just for demonstration purpose only.
 */

package com.awsblogs.makeshift.requests.process
import org.apache.spark.sql._
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.rank

// spark-submit --class com.awsblogs.makeshift.requests.process.AccidentsByDaySparkJob --master yarn --deploy-mode cluster  s3://xxxx-us-west-2/aws-blog-makeshift-requests/example-spark-job-1.0-SNAPSHOT.jar input/ output-emr-parquet/

object AccidentsSparkJob {
  def main(args: Array[String]) =
  {
    if(args.length != 3)
    {
      println("Requires 3 parameters")
      println("Usage: <sourceBucket> <s3InputLocation> <s3OutputLocation>")
      System.exit(-1)
    }
    val s3BucketName = args(0)
    val s3InputLocation = args(1)
    val s3OutputLocation = args(2)

    val spark = SparkSession
      .builder()
      .appName("AccidentsByDaySparkJob")
      .getOrCreate()

    // Top 5 hours in every month when the accidents were more.

    // val inputDF = spark.read.json("s3://" + s3BucketName + "/" + s3InputLocation + "/").toDF()
    val inputDF = spark.read.json( "s3://skkodali-proserve/makeshift-requests/input/monroe-county-crash-data2003-to-2015.csv").toDF()
    val byMonthDF = Window.partitionBy("Month").orderBy("incident_count")
    val countDF =  inputDF.groupBy("Month", "Hour").count()
                    .withColumnRenamed("count", "incident_count")
                    .withColumn("rank", rank().over(byMonthDF))
                    .filter("rank <= 5")
    countDF.write
      .mode("append")
      .parquet("s3://" + s3BucketName + "/" + s3OutputLocation)
  }
}