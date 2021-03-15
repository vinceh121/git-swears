package me.vinceh121.gitswears.graph;

import java.awt.image.BufferedImage;

import org.jfree.chart.JFreeChart;

import me.vinceh121.gitswears.CountSummary;

public abstract class GraphGenerator {
	private final CountSummary summary;
	protected int width = 500, height = 400;
	protected String title;

	public GraphGenerator(final CountSummary summary) {
		this.summary = summary;
	}

	public abstract JFreeChart generateChart();

	public BufferedImage generateImage() {
		final JFreeChart chart = this.generateChart();
		final BufferedImage img = chart.createBufferedImage(this.width, this.height);
		return img;
	}

	public int getWidth() {
		return this.width;
	}

	public void setWidth(final int width) {
		this.width = width;
	}

	public int getHeight() {
		return this.height;
	}

	public void setHeight(final int height) {
		this.height = height;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	public CountSummary getSummary() {
		return summary;
	}
}
