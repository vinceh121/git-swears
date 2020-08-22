package me.vinceh121.gitswears.graph;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.UnknownKeyException;
import org.jfree.data.category.DefaultCategoryDataset;

import me.vinceh121.gitswears.SwearCounter;
import me.vinceh121.gitswears.WordCount;

public class TotalSwearHistogram extends GraphGenerator {
	public TotalSwearHistogram(SwearCounter counter) {
		super(counter);
	}

	@Override
	public JFreeChart generateChart() {
		final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		for (final WordCount count : this.getCounter().getFinalCount().values()) {
			try {
				dataset.incrementValue(count.getEffectiveCount(), count.getWord(), "");
			} catch (final UnknownKeyException e) {
				dataset.setValue(count.getEffectiveCount(), count.getWord(), "");
			}
		}
		final JFreeChart chart
				= ChartFactory.createBarChart(title, "Swears", "Total count (in code and commit messages)", dataset);
		chart.getCategoryPlot().getRangeAxis().setMinorTickMarksVisible(false);
		return chart;
	}
}
