package me.vinceh121.gitswears.service.requests;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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

	public GraphRequest(final SwearService swearService) {
		super(swearService, "graph");
	}

	@Override
	protected void validateSyntax(final RoutingContext ctx) {
		final String type = ctx.request().getParam("type");
		if (type == null) {
			throw new RuntimeException("missing field type");
		}
	}

	@Override
	protected List<String> getExtraJobKey(final RoutingContext ctx) {
		return Arrays.asList(ctx.request().getParam("type"));
	}

	@Override
	protected void sendCached(final RoutingContext ctx, final Response redisRes) { // TODO clean up this huge dupe code
		final String type = ctx.request().getParam("type");

		final int width;
		try {
			width = Integer.parseInt(ctx.request().getParam("width"));
			if (width > 1920)
				throw new NumberFormatException();
		} catch (final NumberFormatException e) {
			this.error(ctx, 400, "Invalid width");
			return;
		}

		final int height;
		try {
			height = Integer.parseInt(ctx.request().getParam("height"));
			if (height > 1920)
				throw new NumberFormatException();
		} catch (final NumberFormatException e) {
			this.error(ctx, 400, "Invalid height");
			return;
		}

		final CountSummary sum = new JsonObject(redisRes.toString()).mapTo(CountSummary.class);

		this.generateImage(sum, type, width, height).onSuccess(img -> {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				ImageIO.write(img, "png", out);
				out.flush();
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			ctx.response().putHeader("Content-Type", "image/png");
			ctx.response().end(Buffer.buffer(out.toByteArray()));
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

	@Override
	protected void sendResult(final RoutingContext ctx, final SwearCounter counter, final Promise<JsonObject> promise) {
		final String type = ctx.request().getParam("type");

		final int width;
		try {
			width = Integer.parseInt(ctx.request().getParam("width"));
			if (width > 1920)
				throw new NumberFormatException();
		} catch (final NumberFormatException e) {
			this.error(ctx, 400, "Invalid width");
			return;
		}

		final int height;
		try {
			height = Integer.parseInt(ctx.request().getParam("height"));
			if (height > 1920)
				throw new NumberFormatException();
		} catch (final NumberFormatException e) {
			this.error(ctx, 400, "Invalid height");
			return;
		}

		final CountSummary sum = counter.generateSummary();

		this.generateImage(sum, type, width, height).onSuccess(img -> {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				ImageIO.write(img, "png", out);
				out.flush();
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			ctx.response().putHeader("Content-Type", "image/png");
			ctx.response().setStatusCode(201).end(Buffer.buffer(out.toByteArray()));
			promise.complete(JsonObject.mapFrom(sum));
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

	@Override
	protected String putInCache(final JsonObject img) {
		return img.encode();
	}

	private Future<BufferedImage> generateImage(final CountSummary sum, final String type, final int width,
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

			promise.complete(gen.generateImage());
		});
	}

}
