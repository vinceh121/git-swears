package me.vinceh121.gitswears;

import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CommitCount {
	@JsonProperty
	private final Map<String, WordCount> words = new Hashtable<>();
	@JsonProperty
	private Date commitDate;
	@JsonIgnore
	private AbbreviatedObjectId commitId;

	public WordCount getOrNew(final String word) {
		final WordCount counter;
		if (this.words.containsKey(word)) {
			counter = this.words.get(word);
		} else {
			counter = new WordCount();
			counter.setWord(word);
			this.words.put(word, counter);
		}
		return counter;
	}

	public WordCount get(String word) {
		return this.words.get(word);
	}

	public Collection<WordCount> counts() {
		return words.values();
	}

	public Set<String> words() {
		return words.keySet();
	}

	public AbbreviatedObjectId getCommitId() {
		return this.commitId;
	}

	public void setCommitId(final AbbreviatedObjectId commitId) {
		this.commitId = commitId;
	}

	public Date getCommitDate() {
		return commitDate;
	}

	public void setCommitDate(final Date commitDate) {
		this.commitDate = commitDate;
	}
}
