package me.vinceh121.gitswears.graph;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
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

	@Override
	public JFreeChart generateChart() {
		final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		for (final WordCount count : this.getSummary().getHistogram().values()) {
			try {
				dataset.incrementValue(count.getEffectiveCount(), count.getWord(), "");
			} catch (final UnknownKeyException e) {
				dataset.setValue(count.getEffectiveCount(), count.getWord(), "");
			}
		}
		final JFreeChart chart = ChartFactory.createBarChart(this.title, "Swears",
				"Total count (in code and commit messages)", dataset);
		chart.getCategoryPlot().getRangeAxis().setMinorTickMarksVisible(false);
		return chart;
	}
}
