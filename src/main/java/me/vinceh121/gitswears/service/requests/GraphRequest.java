package me.vinceh121.gitswears.service.requests;

import java.util.Map;

import javax.imageio.ImageIO;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.CommitCount;
import me.vinceh121.gitswears.service.GitRequest;
import me.vinceh121.gitswears.service.SwearService;

public class GraphRequest extends GitRequest {

	public GraphRequest(SwearService swearService) {
		super(swearService, "graph");
	}

	@Override
	protected void sendCached(RoutingContext ctx, Response redisRes) {
		ctx.response().putHeader("Content-Type", "image/png");
		redisRes.toBytes()
	}

	@Override
	protected void sendResult(RoutingContext ctx, Map<AbbreviatedObjectId, CommitCount> countMap) {
	}

	@Override
	protected String putInCache(Map<AbbreviatedObjectId, CommitCount> countMap) {
		return null;
	}

}
