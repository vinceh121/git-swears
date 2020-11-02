package me.vinceh121.gitswears.service.json;

import java.io.IOException;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class AbbreviatedObjectIdKeySerializer extends JsonSerializer<AbbreviatedObjectId> {

	@Override
	public void serialize(final AbbreviatedObjectId value, final JsonGenerator gen,
			final SerializerProvider serializers) throws IOException {
		gen.writeFieldName(value.name());
	}

}
