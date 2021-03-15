package me.vinceh121.gitswears;

import java.util.Date;
import java.util.Hashtable;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CommitCount extends Hashtable<String, WordCount> {
	private static final long serialVersionUID = 5486389147758111103L;
	@JsonProperty
	private Date commitDate;
	private AbbreviatedObjectId commitId;

	public AbbreviatedObjectId getCommitId() {
		return this.commitId;
	}

	public void setCommitId(final AbbreviatedObjectId commitId) {
		this.commitId = commitId;
	}

	public WordCount getOrNew(final String word) {
		final WordCount counter;
		if (this.containsKey(word)) {
			counter = this.get(word);
		} else {
			counter = new WordCount();
			counter.setWord(word);
			this.put(word, counter);
		}
		return counter;
	}

	public Date getCommitDate() {
		return commitDate;
	}

	public void setCommitDate(final Date commitDate) {
		this.commitDate = commitDate;
	}
}
