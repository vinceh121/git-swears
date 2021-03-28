package me.vinceh121.gitswears.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Stroke;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartTheme;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.plot.DefaultDrawingSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.web.Router;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import me.vinceh121.gitswears.service.json.AbbreviatedObjectIdDeserializer;
import me.vinceh121.gitswears.service.json.AbbreviatedObjectIdKeySerializer;
import me.vinceh121.gitswears.service.json.AbbreviatedObjectIdSerializer;
import me.vinceh121.gitswears.service.requests.GraphRequest;
import me.vinceh121.gitswears.service.requests.JsonRequest;

public class SwearService {
	public static final MetricRegistry METRIC_REGISTRY = new MetricRegistry();
	private static final Logger LOG = LoggerFactory.getLogger(SwearService.class);
	private final Properties config = new Properties();
	private final Collection<String> allowedHosts
			= Arrays.asList("github.com", "gitlab.com", "codeberg.org", "git.savannah.gnu.org");
	private final Collection<String> swearList = new Vector<>();
	private final Vertx vertx;
	private final HttpServer server;
	private final Router router;
	private final Path rootDir;
	private final RedisAPI redisApi;

	public static ChartTheme createMaterialTheme() {
		final Color[] c = new Color[] { new Color(0x263238), new Color(0xff9800), new Color(0x8bc34a),
				new Color(0xffc107), new Color(0x03a9f4), new Color(0xe91e63), new Color(0x009688), new Color(0xcfd8dc),
				new Color(0x37474f), new Color(0xffa74d), new Color(0x9ccc65), new Color(0xffa000), new Color(0x81d4fa),
				new Color(0xad1457), new Color(0x26a69a), new Color(0xeceff1), };
		final Color foreground = c[15];
		final Color background = c[0];

		final StandardChartTheme theme = new StandardChartTheme("Material", true);
		theme.setTitlePaint(foreground);
		theme.setSubtitlePaint(foreground);
		theme.setLegendItemPaint(foreground);
		theme.setBaselinePaint(foreground);
		theme.setLabelLinkPaint(foreground);
		theme.setTickLabelPaint(foreground);
		theme.setAxisLabelPaint(foreground);
		theme.setItemLabelPaint(foreground);
		theme.setLegendBackgroundPaint(background);
		theme.setChartBackgroundPaint(background);
		theme.setPlotBackgroundPaint(background);
		theme.setPlotOutlinePaint(c[8]);
		theme.setCrosshairPaint(c[5]);
		theme.setShadowPaint(c[8]);
		theme.setErrorIndicatorPaint(c[13]);
		theme.setGridBandPaint(foreground);
		theme.setGridBandAlternatePaint(foreground);
		theme.setDrawingSupplier(new DefaultDrawingSupplier(new Paint[] {
				// c[0],
				c[1], c[2], c[3], c[4], c[5], c[6],
				// c[7],
				// c[8],
				c[9], c[10], c[11], c[12], c[13], c[14],
				// c[15],
		}, new Paint[] { c[3], c[5] }, new Stroke[] { new BasicStroke(2.0f) }, new Stroke[] { new BasicStroke(0.5f) },
				DefaultDrawingSupplier.DEFAULT_SHAPE_SEQUENCE));
		return theme;
	}

	public static void main(final String[] args) {
		ChartFactory.setChartTheme(SwearService.createMaterialTheme());

		final SimpleModule gitModule = new SimpleModule();
		gitModule.addSerializer(AbbreviatedObjectId.class, new AbbreviatedObjectIdSerializer());
		gitModule.addDeserializer(AbbreviatedObjectId.class, new AbbreviatedObjectIdDeserializer());
		gitModule.addKeySerializer(AbbreviatedObjectId.class, new AbbreviatedObjectIdKeySerializer());
		DatabindCodec.mapper().registerModule(gitModule);

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
				line = line.trim();
				if (!line.startsWith("#")) {
					this.swearList.add(line);
				}
			}
			br.close();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		this.initMetrics();

		try {
			this.vertx = Vertx.vertx(new VertxOptions(
					new JsonObject(new String(Files.readAllBytes(Paths.get("/etc/gitswears/vertx.json")))))
							.setMetricsOptions(this.config.containsKey("metrics.host")
									? new DropwizardMetricsOptions().setEnabled(true)
									: new MetricsOptions()));
		} catch (final IOException e) {
			throw new RuntimeException("Failed to load vertx config", e);
		}

		this.vertx.exceptionHandler(t -> SwearService.LOG.error("Unexpected Vert.x exception", t));

		final Redis redis = Redis.createClient(this.vertx, this.config.getProperty("redis.constring"));
		redis.connect(redisRes -> {
			if (redisRes.failed()) {
				SwearService.LOG.error("Failed to connect to redis", redisRes.cause());
			}
		});
		this.redisApi = RedisAPI.api(redis);

		this.server = this.vertx.createHttpServer();
		this.server.exceptionHandler(t -> SwearService.LOG.error("Unexpected error in HTTP server", t));
		this.router = Router.router(this.vertx);
		this.router.errorHandler(500,
				ctx -> SwearService.LOG.error("Unexpected exception in route " + ctx.normalisedPath(), ctx.failure()));
		this.server.requestHandler(this.router);

		this.router.get("/count.json").handler(new JsonRequest(this));
		this.router.get("/count.png").handler(new GraphRequest(this, false));
		this.router.get("/count.svg").handler(new GraphRequest(this, true));

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			this.vertx.fileSystem().deleteRecursive(this.rootDir.toAbsolutePath().toString(), true, res -> {
				if (res.failed()) {
					SwearService.LOG.error("Failed to delete temporary folder", res.cause());
				} else {
					SwearService.LOG.info("Deleted temporary folder");
				}
			});
		}, "Temporary folder deletion"));
	}

	private void start() {
		this.server.listen(Integer.parseInt(this.config.getProperty("http.port")), this.config.getProperty("http.host"),
				res -> {
					if (res.succeeded()) {
						LOG.info("Started on port {} !", res.result().actualPort());
					} else {
						LOG.error("Error while starting server", res.cause());
					}
				});
	}

	private void initMetrics() {
		if (!this.config.containsKey("metrics.host")) {
			return;
		}
		final String host = this.config.getProperty("metrics.host");
		final int port = Integer.parseInt(this.config.getProperty("metrics.port"));
		final long period = Long.parseLong(this.config.getProperty("metrics.period"));

		SwearService.LOG.info("Starting metrics");

		SwearService.METRIC_REGISTRY.registerAll("git-swears-gc", new GarbageCollectorMetricSet());
		SwearService.METRIC_REGISTRY.registerAll("git-swears-mem", new MemoryUsageGaugeSet());

		final Graphite graphite = new Graphite(new InetSocketAddress(host, port));
		final GraphiteReporter graphiteReporter = GraphiteReporter.forRegistry(SwearService.METRIC_REGISTRY)
				.prefixedWith("git-swears")
				.convertRatesTo(TimeUnit.MILLISECONDS)
				.convertDurationsTo(TimeUnit.MILLISECONDS)
				.filter(MetricFilter.ALL)
				.build(graphite);

		graphiteReporter.start(period, TimeUnit.MINUTES);
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
