package me.vinceh121.gitswears;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.LfsFactory;

public class SwearCounter {
	private static final Pattern WORD_PATTERN = Pattern.compile("\\W*\\w+\\W*", Pattern.CASE_INSENSITIVE);
	private final Map<AbbreviatedObjectId, CommitCount> map = new HashMap<>();
	private final Collection<String> swears;
	private final Git git;
	private final ObjectReader reader;
	private final ContentSource source;
	private final ContentSource.Pair sourcePair;

	public static void main(String[] args) {
		try {
			final SwearCounter counter
					= new SwearCounter(new FileRepository("/home/vincent/StudioProjects/cj-getter/.git"),
							Arrays.asList("fucking", "fuck"));
			counter.count();
			System.out.println(counter.getMap());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (BinaryBlobException e) {
			e.printStackTrace();
		}
	}

	public SwearCounter(final Repository repo, final Collection<String> swears) {
		this.git = Git.wrap(repo);
		this.swears = swears;
		this.reader = repo.newObjectReader();
		this.source = ContentSource.create(this.reader);
		this.sourcePair = new ContentSource.Pair(source, source);
	}

	public void count() throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
			IOException, BinaryBlobException {
		final PlotWalk walk = new PlotWalk(git.getRepository());
		walk.markStart(walk.parseCommit(git.getRepository().findRef("master").getObjectId()));
		for (final RevCommit c : walk) {
			this.countMessage(c);
			if (c.getParentCount() == 0) {
				continue;
			}
			 final TreeWalk treeWalk = new TreeWalk(git.getRepository());
			 treeWalk.addTree(c.getId());
			 treeWalk.addTree(c.getParent(0).getId());
			 
//			final DiffFormatter diffFmt = new DiffFormatter(NullOutputStream.INSTANCE);
//			diffFmt.setRepository(repo);
//			System.out.println(c.getId());
//			final List<DiffEntry> diff = diffFmt.scan(c.getId(), c.getParent(0).getId());
			for (final DiffEntry e : diff) {
				this.countDiff(e);
			}
			diffFmt.close();
			// treeWalk.close();
		}
		walk.close();
	}

	private void countMessage(final RevCommit commit) {
		final Matcher matcher = WORD_PATTERN.matcher(commit.getFullMessage());
		while (matcher.find()) {
			final String word = matcher.group().toLowerCase().trim();
			if (swears.contains(word))
				this.getOrNewCommitCount(AbbreviatedObjectId.fromObjectId(commit.getId()))
						.getOrNew(word)
						.increaseMessage();

		}
	}

	private void countDiff(final DiffEntry e) throws IOException, BinaryBlobException {
		final String txt = new String(this.open(Side.NEW, e).getRawContent());
//		System.out.println(txt);
		System.out.println("------------------------");
		final Matcher matcher = WORD_PATTERN.matcher(txt);
		while (matcher.find()) {
			final String word = matcher.group().toLowerCase().trim();
			if (swears.contains(word))
				switch (e.getChangeType()) {
				case ADD:
					this.getOrNewCommitCount(e.getNewId()).getOrNew(word).increaseAdded();
					break;
				case DELETE:
					this.getOrNewCommitCount(e.getNewId()).getOrNew(word).increaseRemoved();
					break;
				default:
					break;
				}
		}
	}

	private CommitCount getOrNewCommitCount(final AbbreviatedObjectId id) {
		final CommitCount count;
		if (this.map.containsKey(id)) {
			count = this.map.get(id);
		} else {
			count = new CommitCount();
			count.setCommitId(id);
			this.map.put(id, count);
		}
		return count;
	}

	/**
	 * From org.eclipse.jgit.diff.DiffFormatter under EDL license
	 */
	private RawText open(DiffEntry.Side side, DiffEntry entry) throws IOException, BinaryBlobException {
		if (entry.getMode(side) == FileMode.MISSING)
			return RawText.EMPTY_TEXT;

		if (entry.getMode(side).getObjectType() != Constants.OBJ_BLOB)
			return RawText.EMPTY_TEXT;

		final ObjectLoader ldr = LfsFactory.getInstance()
				.applySmudgeFilter(repo, sourcePair.open(side, entry), entry.getDiffAttribute());
		try {
			return RawText.load(ldr, PackConfig.DEFAULT_BIG_FILE_THRESHOLD);
		} catch (final BinaryBlobException e) {
			return RawText.EMPTY_TEXT;
		}
	}

	public Map<AbbreviatedObjectId, CommitCount> getMap() {
		return map;
	}
}
