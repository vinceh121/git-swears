package me.vinceh121.gitswears.service;

import java.util.Map;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import io.vertx.core.json.JsonObject;
import me.vinceh121.gitswears.CommitCount;
import me.vinceh121.gitswears.WordCount;

public final class JsonUtils {
	public static JsonObject countResultToJson(final Map<AbbreviatedObjectId, CommitCount> data) {
		final JsonObject obj = new JsonObject();
		for (final AbbreviatedObjectId oid : data.keySet()) {
			final JsonObject commitObj = new JsonObject();
			obj.put(oid.name(), commitObj);

			final CommitCount count = data.get(oid);
			for (final WordCount wordCount : count.values()) {
				final JsonObject countObj = JsonObject.mapFrom(wordCount);
				commitObj.put(wordCount.getWord(), countObj);
			}
		}
		return obj;
	}
}
