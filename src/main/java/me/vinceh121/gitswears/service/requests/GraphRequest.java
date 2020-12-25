package me.vinceh121.gitswears.service.requests;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.eclipse.jgit.util.Hex;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.SwearCounter;
import me.vinceh121.gitswears.graph.GraphGenerator;
import me.vinceh121.gitswears.graph.TotalSwearHistogram;
import me.vinceh121.gitswears.graph.TotalTimeLine;
import me.vinceh121.gitswears.service.GitRequest;
import me.vinceh121.gitswears.service.SwearService;

public class GraphRequest extends GitRequest<BufferedImage> {

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
	protected void sendCached(final RoutingContext ctx, final Response redisRes) {
		ctx.response().putHeader("Content-Type", "image/png");
		ctx.response().end(Buffer.buffer(Hex.decode(redisRes.toString())));
	}

	@Override
	protected BufferedImage sendResult(final RoutingContext ctx, final SwearCounter counter) {
		final String type = ctx.request().getParam("type");

		final GraphGenerator gen;
		switch (type) {
		case "histogram":
			gen = new TotalSwearHistogram(counter);
			break;
		case "timeline":
			gen = new TotalTimeLine(counter, false);
			break;
		case "timelinecum":
			gen = new TotalTimeLine(counter, true);
			break;
		default:
			throw new RuntimeException("Invalid graph type");
		}

		final BufferedImage img = gen.generateImage();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "png", out);
			out.flush();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		ctx.response().putHeader("Content-Type", "image/png");
		ctx.response().end(Buffer.buffer(out.toByteArray()));
		return img;
	}

	@Override
	protected String putInCache(final BufferedImage img) {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, "png", out);
			out.flush();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return Hex.toHexString(out.toByteArray());
	}

}
