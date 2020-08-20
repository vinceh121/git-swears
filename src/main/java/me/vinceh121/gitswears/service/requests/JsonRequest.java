package me.vinceh121.gitswears.service.requests;

import java.util.Map;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.CommitCount;
import me.vinceh121.gitswears.service.GitRequest;
import me.vinceh121.gitswears.service.JsonUtils;
import me.vinceh121.gitswears.service.SwearService;

public class JsonRequest extends GitRequest {

	public JsonRequest(SwearService swearService) {
		super(swearService, "json");
	}

	@Override
	protected void sendCached(final RoutingContext ctx, Response redisRes) {
		this.response(ctx, 200, new JsonObject(redisRes.toBuffer()));
	}

	@Override
	protected void sendResult(final RoutingContext ctx, Map<AbbreviatedObjectId, CommitCount> countMap) {
		final JsonObject objRes = JsonUtils.countResultToJson(countMap);
		this.response(ctx, 201, objRes);
	}

	@Override
	protected String putInCache(Map<AbbreviatedObjectId, CommitCount> countMap) {
		final JsonObject objRes = JsonUtils.countResultToJson(countMap);
		return objRes.encode();
	}

}
