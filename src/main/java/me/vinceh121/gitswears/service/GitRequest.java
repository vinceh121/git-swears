package me.vinceh121.gitswears.service;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Paths;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Response;
import me.vinceh121.gitswears.SwearCounter;

public abstract class GitRequest<T> implements Handler<RoutingContext> {
	public static final String PROGRESS_VALUE = new JsonObject().put("message", "Counting is in progress").encode();
	private static final Logger LOG = LoggerFactory.getLogger(GitRequest.class);
	private final SwearService swearService;
	private final WorkerExecutor worker;
	private final String requestName;

	public GitRequest(final SwearService swearService, final String requestName) {
		this.swearService = swearService;
		this.requestName = requestName;
		this.worker = this.swearService.getVertx().createSharedWorkerExecutor("job." + requestName);
	}

	@Override
	public void handle(final RoutingContext ctx) {
		ctx.response().putHeader("Content-Type", "application/json");
		final String clientId = ctx.request().getHeader("X-Forwarded-For");
		ctx.put("clientId", clientId);

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
			return;
		}

		if (!this.swearService.getAllowedHosts().contains(uriObj.getHost())) {
			this.error(ctx, 403, "selected host isn't allowed");
			return;
		}

		final String repoId;
		try {
			repoId = URLEncoder.encode(uri, "UTF-8");
		} catch (final UnsupportedEncodingException e1) {
			throw new RuntimeException(e1);
		}

		final String branch = ctx.request().getParam("branch") != null ? ctx.request().getParam("branch") : "master";
		final boolean includeMessages = !("n".equalsIgnoreCase(ctx.request().getParam("messages")));

		final String jobName = String.join(".", this.requestName, repoId, branch, String.valueOf(includeMessages));

		this.fetchCached(jobName).onComplete(cacheRes -> {
			if (cacheRes.succeeded()) {
				LOG.info("[{}] cached response for repo {} branch {}", clientId, repoId, branch);
				if (!cacheRes.result().toString().equals(PROGRESS_VALUE)) {
					this.sendCached0(ctx, cacheRes.result(), null);
				} else {
					this.response(ctx, 102, new JsonObject().put("message", "Request is in progress"));
				}
				return;
			}

			this.markInProgress(jobName);

			this.cloneRepo(uri, branch, repoId, cloneRes -> {
				if (cloneRes.failed()) {
					this.error(ctx, 503, "Clone failed: " + cloneRes.cause());
					LOG.error("[" + clientId + "] clone failed", cloneRes.cause());
					return;
				}
				LOG.info("[{}] clone success for repo {} branch {}", clientId, uri, branch);

				this.countSwears(cloneRes.result().getRepository(), branch, includeMessages, countRes -> {
					if (countRes.failed()) {
						this.error(ctx, 500, "Git error while counting", countRes.cause());
						this.deleteRepo(cloneRes.result().getRepository());
						return;
					}

					final SwearCounter count = countRes.result();
					LOG.info("[{}] count success for repo {}", clientId, uri);

					this.sendResult0(ctx, count, sendRes -> {
						if (sendRes.failed()) {
							this.error(ctx, 400, "Error while sending result", sendRes.cause());
							this.deleteRepo(cloneRes.result().getRepository());
							return;
						}

						this.swearService.getRedisApi()
								.setex(jobName, this.swearService.getConfig().getProperty("redis.cachetime"),
										this.putInCache(sendRes.result()), redisRes -> {});

						this.deleteRepo(cloneRes.result().getRepository());
					});
				});
			});
		});
	}

	private void cloneRepo(final String uri, final String branch, final String repoId,
			final Handler<AsyncResult<Git>> handler) {
		this.worker.executeBlocking(promise -> {
			final File out = Paths.get(this.swearService.getRootDir().toString(), repoId).toFile();
			if (out.exists()) {
				try {
					promise.complete(Git.open(out));
				} catch (final IOException e) {
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
			} catch (final GitAPIException e) {
				promise.fail(e);
			}
		}, handler);
	}

	private void countSwears(final Repository repo, final String branch, final boolean includeMessages,
			final Handler<AsyncResult<SwearCounter>> handler) {
		this.worker.executeBlocking(promise -> {
			final SwearCounter swearCounter = new SwearCounter(repo, this.swearService.getSwearList());
			swearCounter.setMainRef(branch);
			swearCounter.setIncludeMessages(includeMessages);
			try {
				swearCounter.count();
			} catch (IOException | BinaryBlobException | GitAPIException e) {
				promise.fail(e);
				return;
			}
			promise.complete(swearCounter);
		}, handler);
	}

	private void deleteRepo(final Repository repo) {
		if (repo instanceof FileRepository) {
			final FileRepository fileRepo = (FileRepository) repo;
			this.swearService.getVertx()
					.fileSystem()
					.deleteRecursive(fileRepo.getDirectory().getAbsolutePath(), true, null);
		}
	}

	private void markInProgress(final String key) {
		this.swearService.getRedisApi()
				.setex(key, this.swearService.getConfig().getProperty("redis.counttimeout"), PROGRESS_VALUE,
						hndl -> {});
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

	private void sendCached0(final RoutingContext ctx, final Response redisRes, Handler<AsyncResult<Void>> handler) {
		this.worker.executeBlocking(promise -> {
			this.sendCached(ctx, redisRes);
			promise.complete();
		}, handler);
	}

	protected abstract void sendCached(final RoutingContext ctx, final Response redisRes);

	private void sendResult0(final RoutingContext ctx, final SwearCounter counter, Handler<AsyncResult<T>> handler) {
		this.worker.executeBlocking(promise -> {
			promise.complete(this.sendResult(ctx, counter));
		}, handler);
	}

	protected abstract T sendResult(final RoutingContext ctx, final SwearCounter counter);

	protected abstract String putInCache(final T result);

	public void error(final RoutingContext ctx, final int status, final String msg) {
		this.error(ctx, status, msg, new Exception(msg));
	}

	public void error(final RoutingContext ctx, final int status, final String msg, final Throwable t) {
		this.response(ctx, status, new JsonObject().put("error", t.getMessage()).put("message", msg));
		LOG.error("[" + ctx.get("clientId") + "] error sending result", t);
	}

	public void response(final RoutingContext ctx, final int status, final JsonObject content) {
		ctx.response().setStatusCode(status).end(content.put("status", status).toBuffer());
	}

}
