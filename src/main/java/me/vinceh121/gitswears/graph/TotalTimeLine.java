package me.vinceh121.gitswears.graph;

import java.awt.Color;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
	private long total;
	private boolean cumulative;

	public TotalTimeLine(final SwearCounter counter, final boolean cumulative) {
		super(counter);
		this.width = 1024;
		this.height = 500;
		this.cumulative = cumulative;
	}

	private void countCumulative(final TimeSeries serie, final List<CommitCount> values, final RevWalk revWalk) {
		for (final CommitCount c : values) {
			final RevCommit com = revWalk.lookupCommit(c.getCommitId().toObjectId());
			try {
				revWalk.parseHeaders(com);
				revWalk.parseBody(com);
			} catch (final IOException e) {
				e.printStackTrace();
			}
			final Date date = new Date(com.getCommitTime() * 1000L);
			this.total += this.totalEffective(c);
			serie.addOrUpdate(new Second(date), this.total);
		}
	}

	private void countPoint(final TimeSeries serie, final List<CommitCount> values, final RevWalk revWalk) {
		for (final CommitCount c : values) {
			final RevCommit com = revWalk.lookupCommit(c.getCommitId().toObjectId());
			try {
				revWalk.parseHeaders(com);
				revWalk.parseBody(com);
			} catch (final IOException e) {
				e.printStackTrace();
			}
			final Date date = new Date(com.getCommitTime() * 1000L);
			serie.addOrUpdate(new Second(date), this.totalEffective(c));
		}
	}

	@Override
	public JFreeChart generateChart() {
		final RevWalk revWalk = new RevWalk(this.getCounter().getRepo());

		final TimeSeries serie = new TimeSeries("Total swear count");

		final List<CommitCount> values = new ArrayList<>(this.getCounter().getMap().values());
		Collections.reverse(values);
		if (this.cumulative) {
			this.countCumulative(serie, values, revWalk);
		} else {
			this.countPoint(serie, values, revWalk);
		}
		revWalk.close();

		final TimeSeriesCollection dataset = new TimeSeriesCollection(serie);

		final JFreeChart chart = ChartFactory.createTimeSeriesChart(this.title,
				"Date (" + this.timeZone.getDisplayName() + ")",
				this.cumulative ? "Cumulative swear count per commit" : "Swear count change per commit", dataset);

		final XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(Color.LIGHT_GRAY);
		plot.setDomainGridlinePaint(Color.WHITE);
		plot.setRangeGridlinePaint(Color.WHITE);
		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
		plot.setDomainCrosshairVisible(true);
		plot.setRangeCrosshairVisible(true);

		final XYItemRenderer r = plot.getRenderer();
		if (r instanceof XYLineAndShapeRenderer) {
			final XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
			renderer.setDefaultShapesVisible(true);
			renderer.setDefaultShapesFilled(true);
			renderer.setDrawSeriesLineAsPath(true);
			renderer.setSeriesShape(0, new Line2D.Double(0, -5, 0, 5));
		}

		final DateAxis axis = (DateAxis) plot.getDomainAxis();
		axis.setDateFormatOverride(new SimpleDateFormat("dd-MM-yyyy"));

		return chart;
	}

	/**
	 * @return Total for all words in this commit
	 */
	private long totalEffective(final CommitCount c) {
		long value = 0;
		for (final WordCount w : c.values()) {
			if (this.getCounter().isIncludeMessages()) {
				value += w.getAdded() + w.getMessage();
			} else {
				value += w.getAdded();
			}
		}
		return value;
	}

	public TimeZone getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(final TimeZone timeZone) {
		this.timeZone = timeZone;
	}

	public boolean isCummulative() {
		return this.cumulative;
	}
}
