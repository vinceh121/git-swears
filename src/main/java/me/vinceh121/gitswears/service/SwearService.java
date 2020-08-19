package me.vinceh121.gitswears.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Vector;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import me.vinceh121.gitswears.CommitCount;
import me.vinceh121.gitswears.SwearCounter;

public class SwearService {
	private static final Logger LOG = LoggerFactory.getLogger(SwearService.class);
	private final Collection<String> allowedHosts = Arrays.asList("github.com", "gitlab.com");
	private final Collection<String> swearList = new Vector<>();
	private final Vertx vertx;
	private final HttpServer server;
	private final Router router;
	private final Path rootDir;
	private final RateLimiter rateLimiter;
	private final RedisAPI redisApi;

	public static void main(String[] args) {
		final SwearService service = new SwearService();
		service.start();
	}

	public SwearService() {
		this.rootDir = Paths.get("/tmp/gitswears/");
		this.rootDir.toFile().mkdirs();

		try {
			final BufferedReader br = new BufferedReader(new FileReader("./swears.txt"));
			String line;
			while ((line = br.readLine()) != null) {
				swearList.add(line.trim());
			}
			br.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.rateLimiter = new RateLimiter();
		this.rateLimiter.setDelay(30_000);

		this.vertx = Vertx.vertx();

		final Redis redis = Redis.createClient(vertx, "redis://localhost");
		redis.connect(redisRes -> {});
		this.redisApi = RedisAPI.api(redis);

		this.server = vertx.createHttpServer();
		this.router = Router.router(vertx);
		this.server.requestHandler(router);

		this.router.get("/count.json").handler(this::handleSwearCountJson);
	}

	public void start() {
		this.server.listen(8800, "127.0.0.1");
		LOG.info("Started!");
	}

	private void handleSwearCountJson(final RoutingContext ctx) {
		final String clientId = ctx.request().remoteAddress().host();
		if (!this.rateLimiter.canUse(clientId)) {
			this.error(ctx, 429, "your ip is being rate-limited");
			return;
		}
		this.rateLimiter.hit(clientId);

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

		if (!allowedHosts.contains(uriObj.getHost())) {
			this.error(ctx, 403, "selected hot isn't allowed");
			LOG.warn("[{}] host not allowed {}", clientId, uriObj.getHost());
			return;
		}

		final String repoId = URLEncoder.encode(uri, Charset.defaultCharset());

		final String branch = ctx.request().getParam("branch") != null ? ctx.request().getParam("branch") : "master";

		this.fetchCached(repoId, branch).onComplete(cacheRes -> {
			if (cacheRes.succeeded()) {
				LOG.info("[{}] cached response for repo {} branch {}", clientId, repoId, branch);
				this.response(ctx, 200, cacheRes.result());
				return;
			}

			this.cloneRepo(uri, branch, repoId).onComplete(cloneRes -> {
				if (cloneRes.failed()) {
					this.error(ctx, 503, "Clone failed: " + cloneRes.cause());
					LOG.error("[" + clientId + "] clone failed", cloneRes.cause());
					return;
				}
				LOG.info("[{}] clone success for repo {} branch {}", clientId, uri, branch);

				this.vertx.<Map<AbbreviatedObjectId, CommitCount>>executeBlocking(promise -> {
					final SwearCounter swearCounter = new SwearCounter(cloneRes.result().getRepository(), swearList);
					swearCounter.setMainRef(branch);
					try {
						swearCounter.count();
					} catch (IOException | BinaryBlobException | GitAPIException e) {
						promise.fail(e);
						return;
					}
					promise.complete(swearCounter.getMap());
				}, countRes -> {
					if (countRes.failed()) {
						this.error(ctx, 500, "Error while counting");
						LOG.error("[" + clientId + "] Git error in counting", countRes.cause());
						return;
					}

					LOG.info("[{}] count success for repo {}", clientId, uri);
					final JsonObject objRes = JsonUtils.countResultToJson(countRes.result());
					this.response(ctx, 201, objRes);
					this.redisApi.setex(repoId + "." + branch, "86400", objRes.encode(), resRes -> {});
					if (cloneRes.result().getRepository() instanceof FileRepository) {
						final FileRepository fileRepo = (FileRepository) cloneRes.result().getRepository();
						this.vertx.fileSystem().deleteRecursive(fileRepo.getDirectory().getAbsolutePath(), true, null);
					}
				});
			});
		});
	}

	private Future<JsonObject> fetchCached(final String repoId, final String branch) {
		return Future.future(promise -> {
			this.redisApi.get(repoId + "." + branch, res -> {
				if (res.failed() || res.result() == null) {
					promise.fail(res.cause());
					return;
				}
				promise.complete(new JsonObject(res.result().toString()));
			});
		});
	}

	private Future<Git> cloneRepo(final String uri, final String branch, final String repoId) {
		return Future.future(promise -> {
			final File out = Paths.get(rootDir.toString(), repoId).toFile();
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

	private void error(final RoutingContext ctx, final int status, final String message) {
		this.response(ctx, status, new JsonObject().put("error", message));
	}

	private void response(final RoutingContext ctx, final int status, final JsonObject content) {
		ctx.response().setStatusCode(status).end(content.put("status", status).toBuffer());
	}
}
