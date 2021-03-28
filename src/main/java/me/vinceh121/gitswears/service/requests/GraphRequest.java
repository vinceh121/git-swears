package me.vinceh121.gitswears.service.requests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.CountSummary;
import me.vinceh121.gitswears.SwearCounter;
import me.vinceh121.gitswears.graph.GraphGenerator;
import me.vinceh121.gitswears.graph.TotalSwearHistogram;
import me.vinceh121.gitswears.graph.TotalTimeLine;
import me.vinceh121.gitswears.service.GitRequest;
import me.vinceh121.gitswears.service.SwearService;

public class GraphRequest extends GitRequest<JsonObject> {
	private final boolean svg;

	public GraphRequest(final SwearService swearService, final boolean svg) {
		super(swearService, "graph");
		this.svg = svg;
	}

	@Override
	protected void validateSyntax(final RoutingContext ctx) {
		final String type = ctx.request().getParam("type");
		if (type == null) {
			throw new RuntimeException("missing field type");
		}
	}

	@Override
	protected void sendCached(final RoutingContext ctx, final Response redisRes) {
		final CountSummary sum = new JsonObject(redisRes.toString()).mapTo(CountSummary.class);
		this.sendResponse(sum, ctx, 200);
	}

	@Override
	protected void sendResult(final RoutingContext ctx, final SwearCounter counter, final Promise<JsonObject> promise) {
		final CountSummary sum = counter.generateSummary();
		this.sendResponse(sum, ctx, 201);
		promise.complete(JsonObject.mapFrom(sum));
	}

	@Override
	protected String putInCache(final JsonObject img) {
		return img.encode();
	}

	private void sendResponse(final CountSummary sum, final RoutingContext ctx, final int resStatus) {
		this.handleRequest(sum, ctx).onSuccess(gen -> {
			if (this.svg) {
				final String svgContent = gen.generateSvg().getSVGDocument();
				ctx.response().putHeader("Content-Type", "image/svg+xml");
				ctx.response().setStatusCode(resStatus).end(svgContent);
			} else {
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				try {
					ImageIO.write(gen.generateImage(), "png", out);
					out.flush();
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
				ctx.response().putHeader("Content-Type", "image/png");
				ctx.response().setStatusCode(resStatus).end(Buffer.buffer(out.toByteArray()));
			}
		}).onFailure(t -> {
			final int status;
			if (t instanceof IllegalArgumentException) {
				status = 400;
			} else {
				status = 500;
			}
			this.error(ctx, status, t.getMessage(), t);
		});
	}

	private Future<GraphGenerator> handleRequest(final CountSummary sum, final RoutingContext ctx) {
		final String type = ctx.request().getParam("type");

		final int width;
		try {
			width = Integer.parseInt(ctx.request().getParam("width"));
			if (width > 1920) {
				throw new NumberFormatException();
			}
		} catch (final NumberFormatException e) {
			return Future.failedFuture(new IllegalArgumentException("Invalid width"));
		}

		final int height;
		try {
			height = Integer.parseInt(ctx.request().getParam("height"));
			if (height > 1920) {
				throw new NumberFormatException();
			}
		} catch (final NumberFormatException e) {
			return Future.failedFuture(new IllegalArgumentException("Invalid height"));
		}

		return this.generateImage(sum, type, width, height);
	}

	private Future<GraphGenerator> generateImage(final CountSummary sum, final String type, final int width,
			final int height) {
		return Future.future(promise -> {
			final GraphGenerator gen;
			switch (type) {
			case "histogram":
				gen = new TotalSwearHistogram(sum);
				break;
			case "timeline":
				gen = new TotalTimeLine(sum, false);
				break;
			case "timelinecum":
				gen = new TotalTimeLine(sum, true);
				break;
			default:
				promise.fail(new IllegalArgumentException("Invalid graph type"));
				return;
			}

			gen.setWidth(width);
			gen.setHeight(height);

			promise.complete(gen);
		});
	}

}
