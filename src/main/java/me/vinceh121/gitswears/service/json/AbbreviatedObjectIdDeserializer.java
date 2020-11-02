package me.vinceh121.gitswears.service.json;

import java.io.IOException;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class AbbreviatedObjectIdDeserializer extends StdDeserializer<AbbreviatedObjectId> {
	private static final long serialVersionUID = 1L;

	protected AbbreviatedObjectIdDeserializer() {
		super(AbbreviatedObjectId.class);
	}

	@Override
	public AbbreviatedObjectId deserialize(final JsonParser p, final DeserializationContext ctxt)
			throws IOException, JsonProcessingException {
		return AbbreviatedObjectId.fromString(p.getValueAsString());
	}
}