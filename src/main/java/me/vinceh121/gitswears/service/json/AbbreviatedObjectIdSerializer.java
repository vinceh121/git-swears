package me.vinceh121.gitswears.service.json;

import java.io.IOException;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class AbbreviatedObjectIdSerializer extends StdSerializer<AbbreviatedObjectId> {
	private static final long serialVersionUID = 1L;

	public AbbreviatedObjectIdSerializer() {
		super(AbbreviatedObjectId.class);
	}

	@Override
	public void serialize(final AbbreviatedObjectId value, final JsonGenerator gen, final SerializerProvider provider)
			throws IOException {
		gen.writeString(value.name());
	}
}
