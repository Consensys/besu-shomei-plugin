package net.consensys.shomei.util;

import org.hyperledger.besu.datatypes.Hash;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class HashSerializer extends JsonSerializer<Hash> {

  @Override
  public void serialize(
      final Hash value, final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeString(value.toHexString());
  }
}
