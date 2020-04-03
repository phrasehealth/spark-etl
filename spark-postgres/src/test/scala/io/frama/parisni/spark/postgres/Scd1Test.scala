package io.frama.parisni.spark.postgres

import java.sql.Timestamp

import io.frama.parisni.spark.dataframe.DFTool
import org.apache.spark.sql.{DataFrame, QueryTest}
import org.junit.Test

class Scd1Test extends QueryTest with SparkSessionTestWrapper {

  @Test
  def verifyScd1(): Unit = {
    import spark.implicits._

    (
      (1L, "a", "bob", new Timestamp(1), 1) ::
        (3L, "b", "bob", null, 2) ::
        Nil).toDF("id", "key", "cd", "end_date", "hash")
      .write
      .format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("table", "scd1table")
      .save

    val df = (
      (1L, "a", "jim") ::
        (2L, "b", "johm") ::
        Nil).toDF("id", "key", "cd")

    df.write.format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("type", "scd1")
      .option("joinKey", "key")
      .option("table", "scd1table")
      .save

    val result = (
      (1L, "a", "jim") ::
        (2L, "b", "johm") ::
        Nil).toDF("id", "key", "cd")

    checkAnswer(spark.read.format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("query", "select * from scd1table")
      .load
      .select("id", "key", "cd")
      , result)

  }

  @Test
  def verifyScd1LowLevel(): Unit = {
    import spark.implicits._

    val table: String = "test_scd1_low"
    val joinKey: List[String] = "key1" :: "key2" :: Nil
    val endDatetimeCol: String = "end_date"
    val partitions: Option[Int] = Some(2)
    val multiline: Option[Boolean] = Some(false)
    val pk: String = "id"

    val df: DataFrame = ((1, "a", "b", "jim", new Timestamp(1L), -1) ::
      Nil).toDF("id", "key1", "key2", "cd", "end_date", "hash")
    getPgTool().tableCreate("test_scd1_low", df.schema)
    getPgTool().outputScd1Hash(table, joinKey, DFTool.dfAddHash(df.drop("end_date", "hash")))

    val df1: DataFrame = ((1, "a", "b", "bob", new Timestamp(1L), -1) ::
      Nil).toDF("id", "key1", "key2", "cd", "end_date", "hash")
    getPgTool().outputScd1Hash(table, joinKey, DFTool.dfAddHash(df1.drop("end_date", "hash")))
    // this should make nothing
    getPgTool().outputScd1Hash(table, joinKey, DFTool.dfAddHash(df1.drop("end_date", "hash")))

    val result = getPgTool().inputBulk("select * from test_scd1_low")

    val goal: DataFrame = (
      ("a", "b", "bob") ::
        Nil).toDF("key1", "key2", "cd")

    checkAnswer(
      result.select("key1", "key2", "cd")
      , goal
    )
  }


  @Test
  def verifyScd1WithFilter(): Unit = {
    import spark.implicits._

    ((1L, "a", "bob", new Timestamp(1), 1) ::
      (3L, "b", "bob", null, 2) ::
      Nil).toDF("id", "key", "cd", "end_date", "hash")
      .write
      .format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("table", "scd1table")
      .save

    val df = (
      (1L, "a", "jim") ::
        (2L, "b", "johm") ::
        Nil).toDF("id", "key", "cd")

    df.write.format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("type", "scd1")
      .option("filter", "key is not null")
      .option("joinKey", "key")
      .option("table", "scd1table")
      .save

    val result = (
      (1L, "a", "jim") ::
        (2L, "b", "johm") ::
        Nil).toDF("id", "key", "cd")

    checkAnswer(spark.read.format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("query", "select * from scd1table")
      .load
      .select("id", "key", "cd")
      , result)

  }

  @Test
  def verifyScd1WithFilterAndDelete(): Unit = {
    import spark.implicits._

    ((1L, "a", "bob", true, 1) ::
      (3L, "b", "bob", true, 2) ::
      Nil).toDF("id", "key", "cd", "is_active", "hash")
      .write
      .format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("table", "scd1table")
      .save

    val df = (
      (1L, "a", "jim") ::
        Nil).toDF("id", "key", "cd")

    df.write.format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("type", "scd1")
      .option("filter", "key is not null")
      .option("deleteSet", "is_active = false")
      .option("joinKey", "key")
      .option("table", "scd1table")
      .save

    val result = (
      (1L, "a", "jim", true) ::
        (3L, "b", "bob", false) ::
        Nil).toDF("id", "key", "cd", "is_active")

    checkAnswer(spark.read.format("io.frama.parisni.spark.postgres")
      .option("url", getPgUrl)
      .option("query", "select * from scd1table")
      .load
      .select("id", "key", "cd", "is_active")
      , result)

  }
}
