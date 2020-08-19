package me.vinceh121.gitswears;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.LfsFactory;

public class SwearCounter {
	private static final Pattern WORD_PATTERN = Pattern.compile("\\W*\\w+\\W*", Pattern.CASE_INSENSITIVE);
	private final Map<AbbreviatedObjectId, CommitCount> map = new LinkedHashMap<>();
	private final Collection<String> swears;
	private final Git git;
	private final Repository repo;
	private final ObjectReader reader;
	private final ContentSource source;
	private final ContentSource.Pair sourcePair;
	private String mainRef = "master";

	public SwearCounter(final Repository repo, final Collection<String> swears) {
		this.git = Git.wrap(repo);
		this.repo = repo;
		this.swears = swears;
		this.reader = repo.newObjectReader();
		this.source = ContentSource.create(this.reader);
		this.sourcePair = new ContentSource.Pair(source, source);
	}

	public void count() throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
			IOException, BinaryBlobException, GitAPIException {
		final RevWalk revWalk = new RevWalk(repo);
		revWalk.markStart(repo.parseCommit(repo.findRef(mainRef).getObjectId()));
		for (final RevCommit c : revWalk) {
			this.countMessage(c);
			if (c.getParentCount() == 0) {
				continue;
			}

			final ObjectId oldHead = c.getParent(0).getTree();
			final ObjectId head = c.getTree();

			final CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
			oldTreeIter.reset(reader, oldHead);
			final CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(reader, head);

			for (final DiffEntry e : this.git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call()) {
				this.countDiff(e);
			}
		}
		revWalk.close();
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
		final String newTxt = new String(this.open(Side.NEW, e).getRawContent());
		final String oldTxt = new String(this.open(Side.OLD, e).getRawContent());

		switch (e.getChangeType()) {
		case MODIFY:
		case ADD:
			final Matcher newMatcher = WORD_PATTERN.matcher(newTxt);
			while (newMatcher.find()) {
				final String word = newMatcher.group().toLowerCase().trim();
				if (swears.contains(word))
					this.getOrNewCommitCount(e.getNewId()).getOrNew(word).increaseAdded();
			}
			if (e.getChangeType() == ChangeType.ADD)
				break;
		case DELETE:
			final Matcher oldMatcher = WORD_PATTERN.matcher(oldTxt);
			while (oldMatcher.find()) {
				final String word = oldMatcher.group().toLowerCase().trim();
				if (swears.contains(word))
					this.getOrNewCommitCount(e.getNewId()).getOrNew(word).increaseRemoved();
			}
			if (e.getChangeType() == ChangeType.DELETE)
				break;
		default:
			break;
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

	public Map<String, WordCount> countFinal() {
		final Map<String, WordCount> fin = new Hashtable<>();
		final List<AbbreviatedObjectId> ids = new ArrayList<>(this.map.keySet());
		Collections.reverse(ids);

		for (final AbbreviatedObjectId oid : ids) {
			final CommitCount count = this.map.get(oid);
			for (final String word : count.keySet()) {
				final WordCount wCount = count.get(word);

				final WordCount finCount;
				if (fin.containsKey(word)) {
					finCount = fin.get(word);
				} else {
					finCount = new WordCount();
					finCount.setWord(word);
					fin.put(word, finCount);
				}

				finCount.setAdded(finCount.getAdded() + wCount.getAdded());
				finCount.setMessage(finCount.getMessage() + wCount.getMessage());
				finCount.setRemoved(finCount.getRemoved() + wCount.getRemoved());
			}
		}
		return fin;
	}

	public Map<AbbreviatedObjectId, CommitCount> getMap() {
		return map;
	}

	public Repository getRepo() {
		return repo;
	}

	public Git getGit() {
		return git;
	}

	public String getMainRef() {
		return mainRef;
	}

	public void setMainRef(String mainRef) {
		this.mainRef = mainRef;
	}

}
