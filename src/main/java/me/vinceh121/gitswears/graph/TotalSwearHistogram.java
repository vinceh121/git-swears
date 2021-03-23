package me.vinceh121.gitswears.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.UnknownKeyException;
import org.jfree.data.category.DefaultCategoryDataset;

import me.vinceh121.gitswears.CountSummary;
import me.vinceh121.gitswears.WordCount;

public class TotalSwearHistogram extends GraphGenerator {
	public TotalSwearHistogram(final CountSummary counter) {
		super(counter);
		this.width = 600;
		this.height = 500;
	}

	private long getEffectiveValue(final WordCount count) {
		return this.getSummary().isIncludeMessages() ? count.getMessage() + count.getAdded() - count.getRemoved()
				: count.getAdded() - count.getRemoved();
	}

	@Override
	public JFreeChart generateChart() {
		final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		final List<WordCount> values = new ArrayList<>(this.getSummary().getHistogram().values());
		Collections.sort(values, (w1, w2) -> -Long.compare(this.getEffectiveValue(w1), this.getEffectiveValue(w2)));
		for (final WordCount count : values) {
			try {
				dataset.incrementValue(this.getEffectiveValue(count), count.getWord(), "");
			} catch (final UnknownKeyException e) {
				dataset.setValue(this.getEffectiveValue(count), count.getWord(), "");
			}
		}
		final JFreeChart chart = ChartFactory.createBarChart(this.title, "Swears",
				"Total count (in code and commit messages)", dataset);
		// To get f l a t look
		((BarRenderer) chart.getCategoryPlot().getRenderer()).setBarPainter(new StandardBarPainter());
		chart.getCategoryPlot().getRangeAxis().setMinorTickMarksVisible(false);
		return chart;
	}
}
