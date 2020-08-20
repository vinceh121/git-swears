package me.vinceh121.gitswears.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
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

	public static void main(String[] args) {
		final SwearService service = new SwearService();
		service.start();
	}

	public SwearService() {
		this.rootDir = Paths.get("/tmp/gitswears/");
		this.rootDir.toFile().mkdirs();

		try {
			config.load(new FileInputStream("/etc/gitswears/config.properties"));

			final BufferedReader br = new BufferedReader(new FileReader("/etc/gitswears/swears.txt"));
			String line;
			while ((line = br.readLine()) != null) {
				swearList.add(line.trim());
			}
			br.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.vertx = Vertx.vertx();

		final Redis redis = Redis.createClient(vertx, config.getProperty("redis.constring"));
		redis.connect(redisRes -> {});
		this.redisApi = RedisAPI.api(redis);

		this.server = vertx.createHttpServer();
		this.router = Router.router(vertx);
		this.server.requestHandler(router);

		this.router.get("/count.json").handler(new JsonRequest(this));
	}

	public void start() {
		this.server.listen(Integer.parseInt(this.config.getProperty("http.port")),
				this.config.getProperty("http.host"));
		LOG.info("Started!");
	}

	public RedisAPI getRedisApi() {
		return redisApi;
	}

	public Path getRootDir() {
		return rootDir;
	}

	public Vertx getVertx() {
		return vertx;
	}

	public Properties getConfig() {
		return config;
	}

	public Collection<String> getAllowedHosts() {
		return allowedHosts;
	}

	public Collection<String> getSwearList() {
		return swearList;
	}
}
