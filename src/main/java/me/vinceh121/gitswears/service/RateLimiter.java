package me.vinceh121.gitswears.service;

import java.util.Hashtable;
import java.util.Map;

public class RateLimiter {
	private final Map<Comparable<?>, Long> map = new Hashtable<>();
	private long delay;

	public void hit(final Comparable<?> id) {
		this.map.put(id, System.currentTimeMillis());
	}

	public boolean canUse(final Comparable<?> id) {
		final Long lastUse = this.map.get(id);

		if (lastUse == null)
			return true;

		return System.currentTimeMillis() - lastUse > delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public long getDelay() {
		return delay;
	}
}
