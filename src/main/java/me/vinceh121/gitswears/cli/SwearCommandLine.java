package me.vinceh121.gitswears.cli;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

import me.vinceh121.gitswears.SwearCounter;
import me.vinceh121.gitswears.graph.GraphGenerator;
import me.vinceh121.gitswears.graph.TotalSwearHistogram;
import me.vinceh121.gitswears.graph.TotalTimeLine;

public class SwearCommandLine {
	public static final Options CLI_OPTIONS = new Options();
	public static final Map<String, Class<? extends GraphGenerator>> GRAPH_TYPES = new Hashtable<>();
	private final CommandLine cli;
	private final SwearCounter count;

	public static void main(final String[] args) {
		final SwearCommandLine cli = new SwearCommandLine(args);
		try {
			cli.start();
		} catch (Exception e) {
			System.err.println("Error while counting: ");
			e.printStackTrace();
			System.exit(-5);
		}
		try {
			cli.output();
		} catch (Exception e) {
			System.err.println("Error while generating output: ");
			e.printStackTrace();
			System.exit(-6);
		}
	}

	public SwearCommandLine(final String[] args) {
		if (args.length == 0) {
			printHelp();
			System.exit(0);
		}

		final CommandLineParser parse = new DefaultParser();
		try {
			cli = parse.parse(CLI_OPTIONS, args);
		} catch (final org.apache.commons.cli.ParseException e) {
			e.printStackTrace();
			System.exit(-1);
			throw new RuntimeException(e);
		}

		doTerminatingCommands();

		try {
			validateInput(cli);
		} catch (final IllegalArgumentException e) {
			System.err.println("Invalid CLI: " + e.getMessage());
			printHelp();
			System.exit(-1);
		}

		try {
			count = buildCounter();
		} catch (final Exception e) {
			System.err.println("Failed to build counter");
			e.printStackTrace();
			System.exit(-2);
			throw new RuntimeException(e);
		}

		if (cli.hasOption('b'))
			count.setMainRef(cli.getOptionValue('b'));
	}

	public void start() throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
			IOException, BinaryBlobException, GitAPIException {
		count.count();
	}

	public void output() throws Exception {
		if (cli.hasOption('g')) {
			this.outputImg();
		} else {
			this.outputHuman();
		}
	}

	private void outputImg() throws Exception {
		final String imgFormat = cli.hasOption('i') ? cli.getOptionValue('i') : "png";
		final GraphGenerator graph
				= GRAPH_TYPES.get(cli.getOptionValue('g')).getConstructor(SwearCounter.class).newInstance(this.count);

		final BufferedImage img = graph.generateImage();
		ImageIO.write(img, imgFormat, System.out);
	}

	private void outputHuman() { // TODO
		System.out.println(count.getFinalCount());
	}

	public SwearCounter buildCounter() throws IOException {
		final Repository repo = new FileRepository(cli.getOptionValue('r'));
		final List<String> swears = new Vector<>();

		try {
			final URL url = new URL(cli.getOptionValue('s'));
			final BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			String line;
			while ((line = br.readLine()) != null) {
				swears.add(line);
			}
			br.close();
		} catch (final MalformedURLException e) {
			swears.addAll(Arrays.asList(cli.getOptionValue('s').split(",")));
		}

		return new SwearCounter(repo, swears);
	}

	public void doTerminatingCommands() {
		if (cli.hasOption('t')) {
			System.err.println("Available image formats:");
			for (final String fmt : ImageIO.getWriterFormatNames()) {
				System.out.println(fmt);
			}
			System.exit(0);
		}

		if (cli.hasOption('a')) {
			System.err.println("Available graph types:");
			for (final String g : GRAPH_TYPES.keySet()) {
				System.out.println(g);
			}
			System.exit(0);
		}
	}

	public void validateInput(final CommandLine cli) {
		if (!cli.hasOption('r')) {
			throw new IllegalArgumentException("Missing repo path");
		}

		if (!cli.hasOption('s')) {
			throw new IllegalArgumentException("Missing swear list");
		}
	}

	public void printHelp() {
		final HelpFormatter fmt = new HelpFormatter();
		fmt.printHelp("git-swears", CLI_OPTIONS);
	}

	static {
		CLI_OPTIONS.addOption("r", "repo", true, "Path to the git repository");
		CLI_OPTIONS.addOption("b", "branch", true, "Git branch to count in");

		CLI_OPTIONS.addOption("s", "swears", true,
				"Swear list. Either a comma-separated list, or fully qualified URL to a newline-separated list");

		CLI_OPTIONS.addOption("g", "graph", true, "Outputs a graph");
		CLI_OPTIONS.addOption("a", "list-graphs", false, "Lists available graph types");

		CLI_OPTIONS.addOption("i", "image-type", true, "Image type");
		CLI_OPTIONS.addOption("t", "list-image-types", false, "Image type");
	}

	static {
		GRAPH_TYPES.put("histogram", TotalSwearHistogram.class);
		GRAPH_TYPES.put("timeline", TotalTimeLine.class);
	}
}
