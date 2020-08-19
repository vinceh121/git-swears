package me.vinceh121.gitswears.graph;

import java.awt.image.BufferedImage;

import org.jfree.chart.JFreeChart;

import me.vinceh121.gitswears.SwearCounter;

public abstract class GraphGenerator {
	private final SwearCounter counter;
	protected int width = 500, height = 400;
	protected String title;

	public GraphGenerator(final SwearCounter counter) {
		this.counter = counter;
	}

	public abstract JFreeChart generateChart();

	public BufferedImage generateImage() {
		final JFreeChart chart = this.generateChart();
		final BufferedImage img = chart.createBufferedImage(width, height);
		return img;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public SwearCounter getCounter() {
		return counter;
	}
}
