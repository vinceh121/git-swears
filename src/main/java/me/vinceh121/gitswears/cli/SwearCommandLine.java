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
		} catch (final Exception e) {
			System.err.println("Error while counting: ");
			e.printStackTrace();
			System.exit(-5);
		}
		try {
			cli.output();
		} catch (final Exception e) {
			System.err.println("Error while generating output: ");
			e.printStackTrace();
			System.exit(-6);
		}
	}

	public SwearCommandLine(final String[] args) {
		if (args.length == 0) {
			this.printHelp();
			System.exit(0);
		}

		final CommandLineParser parse = new DefaultParser();
		try {
			this.cli = parse.parse(SwearCommandLine.CLI_OPTIONS, args);
		} catch (final org.apache.commons.cli.ParseException e) {
			e.printStackTrace();
			System.exit(-1);
			throw new RuntimeException(e);
		}

		this.doTerminatingCommands();

		try {
			this.validateInput(this.cli);
		} catch (final IllegalArgumentException e) {
			System.err.println("Invalid CLI: " + e.getMessage());
			this.printHelp();
			System.exit(-1);
		}

		try {
			this.count = this.buildCounter();
		} catch (final Exception e) {
			System.err.println("Failed to build counter");
			e.printStackTrace();
			System.exit(-2);
			throw new RuntimeException(e);
		}

		if (this.cli.hasOption('b')) {
			this.count.setMainRef(this.cli.getOptionValue('b'));
		}
	}

	public void start() throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
			IOException, BinaryBlobException, GitAPIException {
		this.count.count();
	}

	public void output() throws Exception {
		if (this.cli.hasOption('g')) {
			this.outputImg();
		} else {
			this.outputHuman();
		}
	}

	private void outputImg() throws Exception {
		final String imgFormat = this.cli.hasOption('i') ? this.cli.getOptionValue('i') : "png";
		final GraphGenerator graph = SwearCommandLine.GRAPH_TYPES.get(this.cli.getOptionValue('g'))
				.getConstructor(SwearCounter.class)
				.newInstance(this.count);

		final BufferedImage img = graph.generateImage();
		ImageIO.write(img, imgFormat, System.out);
	}

	private void outputHuman() { // TODO
		System.out.println(this.count.getFinalCount());
	}

	public SwearCounter buildCounter() throws IOException {
		final Repository repo = new FileRepository(this.cli.getOptionValue('r'));
		final List<String> swears = new Vector<>();

		try {
			final URL url = new URL(this.cli.getOptionValue('s'));
			final BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			String line;
			while ((line = br.readLine()) != null) {
				swears.add(line);
			}
			br.close();
		} catch (final MalformedURLException e) {
			swears.addAll(Arrays.asList(this.cli.getOptionValue('s').split(",")));
		}

		return new SwearCounter(repo, swears);
	}

	public void doTerminatingCommands() {
		if (this.cli.hasOption('t')) {
			System.err.println("Available image formats:");
			for (final String fmt : ImageIO.getWriterFormatNames()) {
				System.out.println(fmt);
			}
			System.exit(0);
		}

		if (this.cli.hasOption('a')) {
			System.err.println("Available graph types:");
			for (final String g : SwearCommandLine.GRAPH_TYPES.keySet()) {
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
		fmt.printHelp("git-swears", SwearCommandLine.CLI_OPTIONS);
	}

	static {
		SwearCommandLine.CLI_OPTIONS.addOption("r", "repo", true, "Path to the git repository");
		SwearCommandLine.CLI_OPTIONS.addOption("b", "branch", true, "Git branch to count in");

		SwearCommandLine.CLI_OPTIONS.addOption("s", "swears", true,
				"Swear list. Either a comma-separated list, or fully qualified URL to a newline-separated list");

		SwearCommandLine.CLI_OPTIONS.addOption("g", "graph", true, "Outputs a graph");
		SwearCommandLine.CLI_OPTIONS.addOption("a", "list-graphs", false, "Lists available graph types");

		SwearCommandLine.CLI_OPTIONS.addOption("i", "image-type", true, "Image type");
		SwearCommandLine.CLI_OPTIONS.addOption("t", "list-image-types", false, "Image type");
	}

	static {
		SwearCommandLine.GRAPH_TYPES.put("histogram", TotalSwearHistogram.class);
		SwearCommandLine.GRAPH_TYPES.put("timeline", TotalTimeLine.class);
	}
}
