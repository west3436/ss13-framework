package io.github.west3436.ss13.testkit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Collects every error logged while it's open, by any logger on any thread: for tests that a fault
 * is logged where it should be, and no more often than it should be. Each line is the logger's name,
 * {@code ": "}, and then the message. Open it with try-with-resources, so it stops listening when the
 * test ends:
 *
 * <pre>{@code
 * try (FaultLog log = new FaultLog()) {
 *     callThatFails();
 *     helper.assertValueEqual(log.naming("my_mod"), 1, "errors naming my_mod");
 * }
 * }</pre>
 *
 * <p>GameTests run side by side, so a test asks only about text that its own fault logs.
 */
public final class FaultLog extends AbstractAppender implements AutoCloseable {
    private static final AtomicInteger OPENED = new AtomicInteger();

    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final Logger root = (Logger) LogManager.getRootLogger();

    /** Starts collecting errors, until {@link #close}. */
    public FaultLog() {
        // Each log has a name of its own, since the root logger keeps one appender of a name.
        super("ss13_testkit_fault_log_" + OPENED.incrementAndGet(), null, null, true, Property.EMPTY_ARRAY);
        start();
        root.addAppender(this);
    }

    /**
     * How many errors have been logged with this text in the logger's name or the message.
     *
     * @param text the text to look for
     * @return the number of errors
     */
    public int naming(String text) {
        return (int) errors.stream().filter(line -> line.contains(text)).count();
    }

    /**
     * The errors a logger has logged with this text in the message.
     *
     * @param logger the logger's name, exactly
     * @param text the text to look for in the message
     * @return the errors, oldest first, each as the logger's name, {@code ": "} and the message
     */
    public List<String> by(String logger, String text) {
        return errors.stream()
                .filter(line -> line.startsWith(logger + ": ")
                        && line.substring(logger.length() + 2).contains(text))
                .toList();
    }

    /**
     * Every error so far, for a failure's message.
     *
     * @return the errors, oldest first
     */
    public List<String> all() {
        return List.copyOf(errors);
    }

    /**
     * Keeps an event if it is an error or worse. Log4j calls it.
     *
     * @param event the logged event
     */
    @Override
    public void append(LogEvent event) {
        if (event.getLevel().isMoreSpecificThan(Level.ERROR)) {
            errors.add(event.getLoggerName() + ": " + event.getMessage().getFormattedMessage());
        }
    }

    /** Stops collecting. What was collected can still be read. */
    @Override
    public void close() {
        root.removeAppender(this);
        stop();
    }
}
