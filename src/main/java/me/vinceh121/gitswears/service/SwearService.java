package me.vinceh121.gitswears.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import me.vinceh121.gitswears.service.requests.GraphRequest;
import me.vinceh121.gitswears.service.requests.JsonRequest;

public class SwearService {
	private static final Logger LOG = LoggerFactory.getLogger(SwearService.class);
	private final Properties config = new Properties();
	private final Collection<String> allowedHosts = Arrays.asList("github.com", "gitlab.com", "codeberg.org");
	private final Collection<String> swearList = new Vector<>();
	private final Vertx vertx;
	private final HttpServer server;
	private final Router router;
	private final Path rootDir;
	private final RedisAPI redisApi;

	public static void main(final String[] args) {
		final SwearService service = new SwearService();
		service.start();
	}

	public SwearService() {
		this.rootDir = Paths.get("/tmp/gitswears/");
		this.rootDir.toFile().mkdirs();

		try {
			this.config.load(new FileInputStream("/etc/gitswears/config.properties"));

			final BufferedReader br = new BufferedReader(new FileReader("/etc/gitswears/swears.txt"));
			String line;
			while ((line = br.readLine()) != null) {
				this.swearList.add(line.trim());
			}
			br.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		this.initMetrics();

		try {
			this.vertx = Vertx.vertx(new VertxOptions(
					new JsonObject(new String(Files.readAllBytes(Paths.get("/etc/gitswears/vertx.json"))))));
		} catch (final IOException e) {
			throw new RuntimeException("Failed to load vertx config", e);
		}

		this.vertx.exceptionHandler(t -> LOG.error("Unexpected Vert.x exception", t));

		final Redis redis = Redis.createClient(this.vertx, this.config.getProperty("redis.constring"));
		redis.connect(redisRes -> {
			if (redisRes.failed()) {
				LOG.error("Failed to connect to redis", redisRes.cause());
			}
		});
		this.redisApi = RedisAPI.api(redis);

		this.server = this.vertx.createHttpServer();
		this.router = Router.router(this.vertx);
		this.router.errorHandler(500,
				ctx -> LOG.error("Unexpected exception in route " + ctx.normalisedPath(), ctx.failure()));
		this.server.requestHandler(this.router);

		this.router.get("/count.json").handler(new JsonRequest(this));
		this.router.get("/count.png").handler(new GraphRequest(this));

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			this.vertx.fileSystem().deleteRecursive(this.rootDir.toAbsolutePath().toString(), true, res -> {
				if (res.failed()) {
					LOG.error("Failed to delete temporary folder", res.cause());
				} else {
					LOG.info("Deleted temporary folder");
				}
			});
		}, "Temporary folder deletion"));
	}

	private void start() {
		this.server.listen(Integer.parseInt(this.config.getProperty("http.port")),
				this.config.getProperty("http.host"));
		LOG.info("Started!");
	}

	private void initMetrics() {
		if (!this.config.containsKey("metrics.port")) {
			return;
		}
		final int port = Integer.parseInt(this.config.getProperty("metrics.port"));

		LOG.info("Starting metrics");

		DefaultExports.initialize();
		try {
			new HTTPServer("127.0.0.1", port, true);
		} catch (IOException e) {
			LOG.error("Failed to start metrics HTTP server", e);
		}
	}

	public RedisAPI getRedisApi() {
		return this.redisApi;
	}

	public Path getRootDir() {
		return this.rootDir;
	}

	public Vertx getVertx() {
		return this.vertx;
	}

	public Properties getConfig() {
		return this.config;
	}

	public Collection<String> getAllowedHosts() {
		return this.allowedHosts;
	}

	public Collection<String> getSwearList() {
		return this.swearList;
	}
}
