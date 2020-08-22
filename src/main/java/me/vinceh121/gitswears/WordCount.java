package me.vinceh121.gitswears;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WordCount {
	private String word;
	private long removed, added, message, effective;

	public String getWord() {
		return this.word;
	}

	public void setWord(final String word) {
		this.word = word;
	}

	public long getRemoved() {
		return this.removed;
	}

	public void setRemoved(final long removed) {
		this.removed = removed;
	}

	public void increaseRemoved() {
		this.removed++;
	}

	public long getAdded() {
		return this.added;
	}

	public void setAdded(final long added) {
		this.added = added;
	}

	public void increaseAdded() {
		this.added++;
	}

	public long getMessage() {
		return this.message;
	}

	public void setMessage(final long message) {
		this.message = message;
	}

	public void increaseMessage() {
		this.message++;
	}

	@JsonIgnore
	public long getEffectiveCount() {
		return this.effective;
	}

	public void setEffectiveCount(final long effective) {
		this.effective = effective;
	}

	@Override
	public String toString() {
		return "WordCount [word="
				+ this.word
				+ ", removed="
				+ this.removed
				+ ", added="
				+ this.added
				+ ", message="
				+ this.message
				+ "]";
	}
}
