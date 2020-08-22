package me.vinceh121.gitswears;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class WordCount {
	private String word;
	private long removed, added, message, effective;

	public String getWord() {
		return word;
	}

	public void setWord(String word) {
		this.word = word;
	}

	public long getRemoved() {
		return removed;
	}

	public void setRemoved(long removed) {
		this.removed = removed;
	}

	public void increaseRemoved() {
		this.removed++;
	}

	public long getAdded() {
		return added;
	}

	public void setAdded(long added) {
		this.added = added;
	}

	public void increaseAdded() {
		this.added++;
	}

	public long getMessage() {
		return message;
	}

	public void setMessage(long message) {
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
		return "WordCount [word=" + word + ", removed=" + removed + ", added=" + added + ", message=" + message + "]";
	}
}
