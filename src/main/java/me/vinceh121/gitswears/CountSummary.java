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
		return timeline;
	}

	public void setTimeline(Map<AbbreviatedObjectId, CommitCount> timeline) {
		this.timeline = timeline;
	}

	public Map<String, WordCount> getHistogram() {
		return histogram;
	}

	public void setHistogram(Map<String, WordCount> histogram) {
		this.histogram = histogram;
	}

	public WordCount getMostUsed() {
		return mostUsed;
	}

	public void setMostUsed(WordCount mostUsed) {
		this.mostUsed = mostUsed;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public boolean isIncludeMessages() {
		return includeMessages;
	}

	public void setIncludeMessages(boolean includeMessages) {
		this.includeMessages = includeMessages;
	}

	public String getMainRef() {
		return mainRef;
	}

	public void setMainRef(String mainRef) {
		this.mainRef = mainRef;
	}

}
