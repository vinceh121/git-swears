package me.vinceh121.gitswears.service.requests;

import java.util.Collections;
import java.util.Map;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.SwearCounter;
import me.vinceh121.gitswears.WordCount;
import me.vinceh121.gitswears.service.GitRequest;
import me.vinceh121.gitswears.service.JsonUtils;
import me.vinceh121.gitswears.service.SwearService;

public class JsonRequest extends GitRequest<JsonObject> {

	public JsonRequest(final SwearService swearService) {
		super(swearService, "json");
	}

	@Override
	protected void sendCached(final RoutingContext ctx, final Response redisRes) {
		this.response(ctx, 200, new JsonObject(redisRes.toBuffer()));
	}

	@Override
	protected JsonObject sendResult(final RoutingContext ctx, final SwearCounter counter) {
		final JsonObject objRes = new JsonObject();
		objRes.put("timeline", JsonUtils.countResultToJson(counter.getMap()));

		final Map<String, WordCount> finalCount = counter.getFinalCount();

		objRes.put("histogram", finalCount);

		final WordCount mostUsed = Collections.max(finalCount.values(),
				(o1, o2) -> Long.compare(o1.getEffectiveCount(), o2.getEffectiveCount()));
		objRes.put("mostUsed", JsonObject.mapFrom(mostUsed));

		long total = 0;
		for (final WordCount c : finalCount.values()) {
			total += c.getEffectiveCount();
		}
		objRes.put("total", total);

		objRes.put("includesMessages", counter.isIncludeMessages());
		objRes.put("mainRef", counter.getMainRef());

		this.response(ctx, 201, objRes);
		return objRes;
	}

	@Override
	protected String putInCache(final JsonObject content) {
		return content.encode();
	}

}
