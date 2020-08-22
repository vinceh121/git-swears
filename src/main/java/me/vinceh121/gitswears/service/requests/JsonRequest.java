package me.vinceh121.gitswears.service.requests;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.SwearCounter;
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
		final JsonObject objRes = JsonUtils.countResultToJson(counter.getMap());
		this.response(ctx, 201, objRes);
		return objRes;
	}

	@Override
	protected String putInCache(final JsonObject content) {
		return content.encode();
	}

}
