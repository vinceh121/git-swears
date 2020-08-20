package me.vinceh121.gitswears.service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.SwearCounter;

public abstract class GitRequest<T> implements Handler<RoutingContext> {
	public static final String PROGRESS_VALUE = new JsonObject().put("message", "Counting is in progress").encode();
	private static final Logger LOG = LoggerFactory.getLogger(GitRequest.class);
	private final SwearService swearService;
	private final String requestName;

	public GitRequest(final SwearService swearService, final String requestName) {
		this.swearService = swearService;
		this.requestName = requestName;
	}

	public void handle(final RoutingContext ctx) {
		ctx.response().putHeader("Content-Type", "application/json");
		final String clientId = ctx.request().getHeader("X-Forwarded-For");

		final String uri = ctx.request().getParam("uri");
		if (uri == null) {
			this.error(ctx, 400, "uri not specified");
			LOG.warn("[{}] uri not specified", clientId);
			return;
		}

		final URI uriObj;
		try {
			uriObj = URI.create(uri);
		} catch (final IllegalArgumentException e) {
			this.error(ctx, 400, "invalid uri");
			LOG.warn("[{}] invalid uri", clientId);
			return;
		}

		if (!this.swearService.getAllowedHosts().contains(uriObj.getHost())) {
			this.error(ctx, 403, "selected host isn't allowed");
			LOG.warn("[{}] host not allowed {}", clientId, uriObj.getHost());
			return;
		}

		final String repoId;
		try {
			repoId = URLEncoder.encode(uri, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			throw new RuntimeException(e1);
		}

		final String branch = ctx.request().getParam("branch") != null ? ctx.request().getParam("branch") : "master";
		final String jobName = String.join(".", requestName, repoId, branch);

		this.fetchCached(jobName).onComplete(cacheRes -> {
			if (cacheRes.succeeded()) {
				LOG.info("[{}] cached response for repo {} branch {}", clientId, repoId, branch);
				if (!cacheRes.result().toString().equals(PROGRESS_VALUE))
					this.sendCached(ctx, cacheRes.result());
				else
					this.error(ctx, 102, "Request is in progress");
				return;
			}

			this.swearService.getRedisApi().set(Arrays.asList(jobName, PROGRESS_VALUE), redisRes -> {});

			this.cloneRepo(uri, branch, repoId).onComplete(cloneRes -> {
				if (cloneRes.failed()) {
					this.error(ctx, 503, "Clone failed: " + cloneRes.cause());
					LOG.error("[" + clientId + "] clone failed", cloneRes.cause());
					return;
				}
				LOG.info("[{}] clone success for repo {} branch {}", clientId, uri, branch);

				this.swearService.getVertx().<SwearCounter>executeBlocking(promise -> {
					final SwearCounter swearCounter
							= new SwearCounter(cloneRes.result().getRepository(), this.swearService.getSwearList());
					swearCounter.setMainRef(branch);
					try {
						swearCounter.count();
					} catch (IOException | BinaryBlobException | GitAPIException e) {
						promise.fail(e);
						return;
					}
					promise.complete(swearCounter);
				}, countRes -> {
					if (countRes.failed()) {
						this.error(ctx, 500, "Error while counting");
						LOG.error("[" + clientId + "] Git error in counting", countRes.cause());
						return;
					}
					final SwearCounter count = countRes.result();
					LOG.info("[{}] count success for repo {}", clientId, uri);
					final T res;
					try {
						res = this.sendResult(ctx, count);
					} catch (final RuntimeException e) {
						this.error(ctx, 400, "Error while sending result: " + e);
						LOG.error("[" + clientId + "] error sending result", e);
						return;
					}

					final String cacheValue = this.putInCache(res);
					if (cacheValue != null)
						this.swearService.getRedisApi()
								.setex(jobName, this.swearService.getConfig().getProperty("redis.cachetime"),
										cacheValue, redisRes -> {});
					if (cloneRes.result().getRepository() instanceof FileRepository) {
						final FileRepository fileRepo = (FileRepository) cloneRes.result().getRepository();
						this.swearService.getVertx()
								.fileSystem()
								.deleteRecursive(fileRepo.getDirectory().getAbsolutePath(), true, null);
					}
				});
			});
		});
	}

	private Future<Git> cloneRepo(final String uri, final String branch, final String repoId) {
		return Future.future(promise -> {
			final File out = Paths.get(this.swearService.getRootDir().toString(), repoId).toFile();
			if (out.exists()) {
				try {
					promise.complete(Git.open(out));
				} catch (IOException e) {
					promise.fail(e);
				}
			}
			final CloneCommand cmd = Git.cloneRepository()
					.setBare(true)
					.setURI(uri)
					.setDirectory(out)
					.setCloneAllBranches(false)
					/* .setBranchesToClone(Collections.singleton(branch)) */
					.setBranch(branch)
					.setTimeout(30);
			try {
				promise.complete(cmd.call());
			} catch (GitAPIException e) {
				promise.fail(e);
			}
		});
	}

	private Future<Response> fetchCached(final String key) {
		return Future.future(promise -> {
			this.swearService.getRedisApi().get(key, res -> {
				if (res.failed() || res.result() == null) {
					promise.fail(res.cause());
					return;
				}
				promise.complete(res.result());
			});
		});
	}

	protected abstract void sendCached(final RoutingContext ctx, final Response redisRes);

	protected abstract T sendResult(final RoutingContext ctx, final SwearCounter counter);

	protected abstract String putInCache(final T result);

	public void error(final RoutingContext ctx, final int status, final String message) {
		this.response(ctx, status, new JsonObject().put("error", message));
	}

	public void response(final RoutingContext ctx, final int status, final JsonObject content) {
		ctx.response().setStatusCode(status).end(content.put("status", status).toBuffer());
	}

}
