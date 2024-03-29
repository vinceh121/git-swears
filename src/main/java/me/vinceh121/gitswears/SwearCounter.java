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
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.MyersDiff;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
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
	// private static final Logger LOG =
	// LoggerFactory.getLogger(SwearCounter.class);
	private static final Pattern WORD_PATTERN = Pattern.compile("\\W*\\w+\\W*", Pattern.CASE_INSENSITIVE);
	private final DiffAlgorithm diffAlg = MyersDiff.INSTANCE;
	private final Map<AbbreviatedObjectId, CommitCount> map = new LinkedHashMap<>();
	private final Map<String, WordCount> fynal = new Hashtable<>();
	private final Collection<String> swears;
	private final Git git;
	private final Repository repo;
	private final ObjectReader reader;
	private final ContentSource source;
	private final ContentSource.Pair sourcePair;
	private boolean includeMessages = true;
	private String mainRef = "master";

	public SwearCounter(final Repository repo, final Collection<String> swears) {
		this.git = Git.wrap(repo);
		this.repo = repo;
		this.swears = swears;
		this.reader = repo.newObjectReader();
		this.source = ContentSource.create(this.reader);
		this.sourcePair = new ContentSource.Pair(this.source, this.source);
	}

	public void count() throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
			IOException, BinaryBlobException, GitAPIException {
		final RevWalk revWalk = new RevWalk(this.repo);
		revWalk.markStart(this.repo.parseCommit(this.repo.findRef(this.mainRef).getObjectId()));
		for (final RevCommit c : revWalk) {
			this.countMessage(c);

			if (c.getParentCount() == 0) {
				continue;
			}

			final ObjectId oldHead = c.getParent(0).getTree();
			final ObjectId head = c.getTree();

			final CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
			oldTreeIter.reset(this.reader, oldHead);
			final CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(this.reader, head);

			for (final DiffEntry e : this.git.diff().setNewTree(newTreeIter).setOldTree(oldTreeIter).call()) {
				this.countDiff(e, AbbreviatedObjectId.fromObjectId(c.getId()));
			}

			final CommitCount count = this.map.get(AbbreviatedObjectId.fromObjectId(c.getId()));
			if (count != null) {
				count.setCommitDate(c.getCommitterIdent().getWhen());
			}
		}
		revWalk.close();
		this.countFinal();
	}

	private void countMessage(final RevCommit commit) {
		final Matcher matcher = SwearCounter.WORD_PATTERN.matcher(commit.getFullMessage());
		while (matcher.find()) {
			final String word = matcher.group().toLowerCase().trim();
			if (this.swears.contains(word)) {
				this.getOrNewCommitCount(AbbreviatedObjectId.fromObjectId(commit.getId()))
						.getOrNew(word)
						.increaseMessage();
			}

		}
	}

	private void countDiff(final DiffEntry entry, final AbbreviatedObjectId oid)
			throws IOException, BinaryBlobException {
		final RawText oldTxt = this.open(Side.OLD, entry);
		final RawText newTxt = this.open(Side.NEW, entry);

		final EditList edits = this.diffAlg.diff(RawTextComparator.DEFAULT, oldTxt, newTxt);

		for (Edit e : edits) {
			switch (e.getType()) {
			case INSERT:
			case REPLACE:
				final String strNew = newTxt.getString(e.getBeginB(), e.getEndB(), false);
				final Matcher newMatcher = SwearCounter.WORD_PATTERN
						.matcher(strNew);
				while (newMatcher.find()) {
					final String word = newMatcher.group().toLowerCase().trim();
					if (this.swears.contains(word)) {
						this.getOrNewCommitCount(oid).getOrNew(word).increaseAdded();
					}
				}
				break;
			case DELETE:
				final String strDel = oldTxt.getString(e.getBeginA(), e.getEndA(), false);
				final Matcher oldMatcher = SwearCounter.WORD_PATTERN
						.matcher(strDel);
				while (oldMatcher.find()) {
					final String word = oldMatcher.group().toLowerCase().trim();
					if (this.swears.contains(word)) {
						this.getOrNewCommitCount(oid).getOrNew(word).increaseRemoved();
					}
				}
				break;
			case EMPTY:
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
	private RawText open(final DiffEntry.Side side, final DiffEntry entry) throws IOException, BinaryBlobException {
		if (entry.getMode(side) == FileMode.MISSING) {
			return RawText.EMPTY_TEXT;
		}

		if (entry.getMode(side).getObjectType() != Constants.OBJ_BLOB) {
			return RawText.EMPTY_TEXT;
		}

		final ObjectLoader ldr = LfsFactory.getInstance()
				.applySmudgeFilter(this.repo, this.sourcePair.open(side, entry), entry.getDiffAttribute());
		try {
			return RawText.load(ldr, PackConfig.DEFAULT_BIG_FILE_THRESHOLD);
		} catch (final BinaryBlobException e) { // if file is binary, ignore it
			return RawText.EMPTY_TEXT;
		}
	}

	private Map<String, WordCount> countFinal() {
		final List<AbbreviatedObjectId> ids = new ArrayList<>(this.map.keySet());
		Collections.reverse(ids);

		for (final AbbreviatedObjectId oid : ids) {
			final CommitCount count = this.map.get(oid);
			for (final String word : count.words()) {
				final WordCount wCount = count.get(word);

				final WordCount finCount;
				if (this.fynal.containsKey(word)) {
					finCount = this.fynal.get(word);
				} else {
					finCount = new WordCount();
					finCount.setWord(word);
					this.fynal.put(word, finCount);
				}

				finCount.setAdded(finCount.getAdded() + wCount.getAdded());
				finCount.setMessage(finCount.getMessage() + wCount.getMessage());
				finCount.setRemoved(finCount.getRemoved() + wCount.getRemoved());

				this.calcEffective(wCount); // TODO put there where it's more optimized
				this.calcEffective(finCount);
			}
		}
		return this.fynal;
	}

	private void calcEffective(final WordCount count) {
		if (this.includeMessages) {
			count.setEffectiveCount(count.getAdded() - count.getRemoved() + count.getMessage());
		} else {
			count.setEffectiveCount(count.getAdded() - count.getRemoved());
		}
	}

	public CountSummary generateSummary() {
		final CountSummary sum = new CountSummary();
		sum.setTimeline(this.getMap());
		sum.setHistogram(this.getFinalCount());

		if (sum.getHistogram().size() != 0) {
			final WordCount mostUsed = Collections.max(sum.getHistogram().values(),
					(o1, o2) -> Long.compare(o1.getEffectiveCount(), o2.getEffectiveCount()));
			sum.setMostUsed(mostUsed);
		}

		long total = 0;
		for (final WordCount c : sum.getHistogram().values()) {
			total += c.getEffectiveCount();
		}
		sum.setTotal(total);

		sum.setIncludeMessages(this.isIncludeMessages());
		sum.setMainRef(this.getMainRef());

		return sum;
	}

	public Map<AbbreviatedObjectId, CommitCount> getMap() {
		return this.map;
	}

	public Map<String, WordCount> getFinalCount() {
		return this.fynal;
	}

	public Repository getRepo() {
		return this.repo;
	}

	public Git getGit() {
		return this.git;
	}

	public String getMainRef() {
		return this.mainRef;
	}

	public void setMainRef(final String mainRef) {
		this.mainRef = mainRef;
	}

	public boolean isIncludeMessages() {
		return this.includeMessages;
	}

	public void setIncludeMessages(final boolean includeMessages) {
		this.includeMessages = includeMessages;
	}

}
