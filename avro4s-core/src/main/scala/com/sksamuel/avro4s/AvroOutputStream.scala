package com.sksamuel.avro4s

import java.io.{File, OutputStream}
import java.nio.file.{Files, Path}

import org.apache.avro.file.DataFileWriter
import org.apache.avro.generic.{GenericDatumWriter, GenericRecord}
import org.apache.avro.io.EncoderFactory

trait AvroOutputStream[T] {
  def close(): Unit
  def flush(): Unit
  def fSync(): Unit
  def write(t: T): Unit
  def write(ts: Seq[T]): Unit = ts.foreach(write)
}

// avro output stream that does not write the schema, only use when you want the smallest messages possible
// at the cost of not having the schema available in the messages for downstream clients
case class AvroBinaryOutputStream[T](os: OutputStream)(implicit schemaFor: SchemaFor[T], toRecord: ToRecord[T])
  extends AvroOutputStream[T] {

  val dataWriter = new GenericDatumWriter[GenericRecord](schemaFor())
  val encoder = EncoderFactory.get().binaryEncoder(os, null)

  override def close(): Unit = {
    encoder.flush()
    os.close()
  }

  override def write(t: T): Unit = dataWriter.write(toRecord(t), encoder)
  override def flush(): Unit = encoder.flush()
  override def fSync(): Unit = ()
}

// avro output stream that includes the schema for the messages. This is usually what you want.
case class AvroDataOutputStream[T](os: OutputStream)(implicit schemaFor: SchemaFor[T], toRecord: ToRecord[T])
  extends AvroOutputStream[T] {

  val datumWriter = new GenericDatumWriter[GenericRecord](schemaFor())
  val dataFileWriter = new DataFileWriter[GenericRecord](datumWriter)
  dataFileWriter.create(schemaFor(), os)

  override def close(): Unit = {
    dataFileWriter.close()
    os.close()
  }

  override def write(t: T): Unit = {
    val record = toRecord(t)
    dataFileWriter.append(record)
  }
  override def flush(): Unit = dataFileWriter.flush()
  override def fSync(): Unit = dataFileWriter.fSync()
}

// avro output stream that writes json instead of a packed format
case class AvroJsonOutputStream[T](os: OutputStream)(implicit schemaFor: SchemaFor[T], toRecord: ToRecord[T])
  extends AvroOutputStream[T] {

  private val schema = schemaFor()
  protected val datumWriter = new GenericDatumWriter[GenericRecord](schema)
  private val encoder = EncoderFactory.get.jsonEncoder(schema, os)

  override def close(): Unit = {
    encoder.flush()
    os.close()
  }

  override def write(t: T): Unit = datumWriter.write(toRecord(t), encoder)
  override def fSync(): Unit = {}
  override def flush(): Unit = encoder.flush()
}

object AvroOutputStream {

  def json[T: SchemaFor : ToRecord](file: File): AvroJsonOutputStream[T] = json(file.toPath)
  def json[T: SchemaFor : ToRecord](path: Path): AvroJsonOutputStream[T] = json(Files.newOutputStream(path))
  def json[T: SchemaFor : ToRecord](os: OutputStream): AvroJsonOutputStream[T] = AvroJsonOutputStream(os)

  def data[T: SchemaFor : ToRecord](file: File): AvroDataOutputStream[T] = data(file.toPath)
  def data[T: SchemaFor : ToRecord](path: Path): AvroDataOutputStream[T] = data(Files.newOutputStream(path))
  def data[T: SchemaFor : ToRecord](os: OutputStream): AvroDataOutputStream[T] = AvroDataOutputStream(os)

  def binary[T: SchemaFor : ToRecord](file: File): AvroBinaryOutputStream[T] = binary(file.toPath)
  def binary[T: SchemaFor : ToRecord](path: Path): AvroBinaryOutputStream[T] = binary(Files.newOutputStream(path))
  def binary[T: SchemaFor : ToRecord](os: OutputStream): AvroBinaryOutputStream[T] = AvroBinaryOutputStream(os)

  @deprecated("Use .json .data or .binary to make it explicit which type of output you want", "1.5.0")
  def apply[T: SchemaFor : ToRecord](file: File): AvroOutputStream[T] = apply(file.toPath, true)

  @deprecated("Use .json .data or .binary to make it explicit which type of output you want", "1.5.0")
  def apply[T: SchemaFor : ToRecord](file: File, binaryModeDisabled: Boolean): AvroOutputStream[T] = apply(file.toPath, binaryModeDisabled)

  @deprecated("Use .json .data or .binary to make it explicit which type of output you want", "1.5.0")
  def apply[T: SchemaFor : ToRecord](path: Path): AvroOutputStream[T] = apply(Files.newOutputStream(path), true)

  @deprecated("Use .json .data or .binary to make it explicit which type of output you want", "1.5.0")
  def apply[T: SchemaFor : ToRecord](path: Path, binaryModeDisabled: Boolean): AvroOutputStream[T] = apply(Files.newOutputStream(path), binaryModeDisabled)

  @deprecated("Use .json .data or .binary to make it explicit which type of output you want", "1.5.0")
  def apply[T: SchemaFor : ToRecord](os: OutputStream): AvroOutputStream[T] = apply(os, false)

  @deprecated("Use .json .data or .binary to make it explicit which type of output you want", "1.5.0")
  def apply[T: SchemaFor : ToRecord](os: OutputStream, binaryModeDisabled: Boolean): AvroOutputStream[T] = {
    if (binaryModeDisabled) new AvroDataOutputStream[T](os)
    else new AvroBinaryOutputStream[T](os)
  }
}