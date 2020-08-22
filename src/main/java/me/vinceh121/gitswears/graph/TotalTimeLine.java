package me.vinceh121.gitswears.graph;

import java.awt.Color;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import me.vinceh121.gitswears.CommitCount;
import me.vinceh121.gitswears.SwearCounter;
import me.vinceh121.gitswears.WordCount;

public class TotalTimeLine extends GraphGenerator {
	private TimeZone timeZone = TimeZone.getDefault();

	public TotalTimeLine(SwearCounter counter) {
		super(counter);
	}

	@Override
	public JFreeChart generateChart() {
		final RevWalk revWalk = new RevWalk(this.getCounter().getRepo());

		final TimeSeries serie = new TimeSeries("Total swear count");

		for (final CommitCount c : this.getCounter().getMap().values()) {
			final RevCommit com = revWalk.lookupCommit(c.getCommitId().toObjectId());
			try {
				revWalk.parseHeaders(com);
				revWalk.parseBody(com);
			} catch (IOException e) {
				e.printStackTrace();
			}
			final Date date = new Date(((long) com.getCommitTime()) * 1000L);
			serie.addOrUpdate(new Second(date), this.totalEffective(c));
		}
		revWalk.close();

		final TimeSeriesCollection dataset = new TimeSeriesCollection(serie);

		final JFreeChart chart = ChartFactory.createTimeSeriesChart(title, "Date (" + timeZone.getDisplayName() + ")",
				"Total swear count", dataset);

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(Color.LIGHT_GRAY);
		plot.setDomainGridlinePaint(Color.WHITE);
		plot.setRangeGridlinePaint(Color.WHITE);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);

		XYItemRenderer r = plot.getRenderer();
		if (r instanceof XYLineAndShapeRenderer) {
			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
			renderer.setDefaultShapesVisible(true);
			renderer.setDefaultShapesFilled(true);
			renderer.setDrawSeriesLineAsPath(true);
		}

		DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setDateFormatOverride(new SimpleDateFormat("MMM-yyyy"));

		return chart;
	}

	private long totalEffective(final CommitCount c) {
		long value = 0;
		for (final WordCount w : c.values()) {
			value += w.getEffectiveCount();
		}
		return value;
	}

	public TimeZone getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
	}
}
