
package com.airhacks.wad.boundary;

import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;

import com.airhacks.wad.control.Builder;
import com.airhacks.wad.control.Copier;
import com.airhacks.wad.control.FolderWatchService;
import com.airhacks.wad.control.TerminalColors;

/**
 *
 * @author airhacks.com
 */
public class WADFlow {
    private final List<Long> buildTimes;

    private final AtomicLong successCounter = new AtomicLong();
    private final AtomicLong buildErrorCounter = new AtomicLong();

    private final Copier copier;
    private final Builder builder;

    public WADFlow(Path dir, Path war, List<Path> deploymentTargets, Builder builder) {
	this.builder = builder;
	this.buildTimes = new ArrayList<>();
	this.copier = new Copier(war, deploymentTargets);
	Runnable changeListener = this::buildAndDeploy;
	changeListener.run();
	registerEnterListener(changeListener);
	FolderWatchService.listenForChanges(dir, changeListener);
    }

    void registerEnterListener(Runnable listener) {
	InputStream in = System.in;
	Runnable task = () -> {
	    int c;
	    try {
		while ((c = in.read()) != -1) {
		    listener.run();
		}
	    } catch (IOException ex) {
	    }
	};
	new Thread(task).start();
    }

    void buildAndDeploy() {
	this.build();
	this.deploy();
    }

    void build() {
	long start = System.currentTimeMillis();
	try {
	    System.out.printf("[%s%s%s]", TerminalColors.TIME.value(), currentFormattedTime(),
		    TerminalColors.RESET.value());
	    InvocationResult result = this.builder.build();
	    if (result.getExitCode() == 0) {
		System.out.printf("[%d]", successCounter.incrementAndGet());
		System.out.print("\uD83D\uDC4D");
		long buildTime = System.currentTimeMillis() - start;
		buildTimes.add(buildTime);
		System.out.println(" built in " + buildTime + " ms");
		if (buildTimes.size() % 10 == 0) {
		    this.printStatistics();
		}
	    } else {
		System.out.printf("[%d] ", buildErrorCounter.incrementAndGet());
		System.out.println("\uD83D\uDC4E ");
	    }
	} catch (MavenInvocationException ex) {
	    System.err.println(ex.getClass().getName() + " " + ex.getMessage());
	}
    }

    void deploy() {
	long start = System.currentTimeMillis();
	this.copier.copy();
	System.out.print("\uD83D\uDE80 ");
	System.out.println(" copied in " + (System.currentTimeMillis() - start) + " ms");

    }

    static String currentFormattedTime() {
	DateTimeFormatter timeFormatter = new DateTimeFormatterBuilder().appendValue(HOUR_OF_DAY, 2).appendLiteral(':')
		.appendValue(MINUTE_OF_HOUR, 2).optionalStart().appendLiteral(':').appendValue(SECOND_OF_MINUTE, 2)
		.toFormatter();

	return LocalTime.now().format(timeFormatter);
    }

    public LongSummaryStatistics buildTimeStatistics() {
	return this.buildTimes.stream().mapToLong(t -> t).summaryStatistics();
    }

    String statisticsSummary() {
	LongSummaryStatistics warSizeStatistics = this.copier.warSizeStatistics();
	long maxKb = warSizeStatistics.getMax();
	long minKb = warSizeStatistics.getMin();
	long totalKb = warSizeStatistics.getSum();
	String warStats = String.format("WAR sizes: min %d kB, max %d kB, total %d kB\n", minKb, maxKb, totalKb);

	LongSummaryStatistics buildTimeStatistics = this.buildTimeStatistics();
	long maxTime = buildTimeStatistics.getMax();
	long minTime = buildTimeStatistics.getMin();
	long totalTime = buildTimeStatistics.getSum();
	String buildTimeStats = String.format("Build times: min %d ms, max %d ms, total %d ms\n", minTime, maxTime,
		totalTime);

	String failureStats;
	long failedBuilds = buildErrorCounter.get();
	if (failedBuilds == 0) {
	    failureStats = "Great! Every build was a success!";
	} else {
	    failureStats = String.format("%d builds failed", buildErrorCounter.get());
	}
	return warStats + buildTimeStats + failureStats;
    }

    void printStatistics() {
	System.out.println(statisticsSummary());
    }

}
