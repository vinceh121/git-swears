package me.vinceh121.gitswears;

import java.util.Map;

import org.eclipse.jgit.lib.AbbreviatedObjectId;

public class CountSummary {
	private Map<AbbreviatedObjectId, CommitCount> timeline;
	private Map<String, WordCount> histogram;
	private WordCount mostUsed;
	private long total;
	private boolean includeMessages;
	private String mainRef;

	public Map<AbbreviatedObjectId, CommitCount> getTimeline() {
		return this.timeline;
	}

	public void setTimeline(final Map<AbbreviatedObjectId, CommitCount> timeline) {
		this.timeline = timeline;
	}

	public Map<String, WordCount> getHistogram() {
		return this.histogram;
	}

	public void setHistogram(final Map<String, WordCount> histogram) {
		this.histogram = histogram;
	}

	public WordCount getMostUsed() {
		return this.mostUsed;
	}

	public void setMostUsed(final WordCount mostUsed) {
		this.mostUsed = mostUsed;
	}

	public long getTotal() {
		return this.total;
	}

	public void setTotal(final long total) {
		this.total = total;
	}

	public boolean isIncludeMessages() {
		return this.includeMessages;
	}

	public void setIncludeMessages(final boolean includeMessages) {
		this.includeMessages = includeMessages;
	}

	public String getMainRef() {
		return this.mainRef;
	}

	public void setMainRef(final String mainRef) {
		this.mainRef = mainRef;
	}

}
