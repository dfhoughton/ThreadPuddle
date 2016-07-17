/*
 * dfh.thread.ThreadPuddle -- a simple, fast thread pool
 *
 * Copyright (C) 2013 David F. Houghton
 *
 * This software is licensed under the LGPL. Please see accompanying NOTICE file
 * and lgpl.txt.
 */
package dfh.thread;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread pool class to simplify running things asynchronously. Basically,
 * what it adds to an ordinary {@link ExecutorService} is {@link #flush()},
 * which you can call after you've submitted all the tasks to run concurrently;
 * it will block until all processes have completed. Also, its task queue is
 * necessarily limited, so it will block if the thread generating work runs too
 * far ahead of the threads doing it.
 * <p>
 * All of a puddle's threads are daemons. If a task given to the puddle throws
 * an error, the errors stack trace will be printed. If you wish different error
 * handling, you must include this in the task itself.
 */
public class ThreadPuddle {

	/**
	 * A thread that knows to check all the various flags and semaphores a
	 * puddle uses.
	 */
	private class PuddleThread extends Thread {
		{
			setDaemon(true);
			start();
		}
		Runnable r;

		@Override
		public void run() {
			OUTER: while (!dead.get()) {
				synchronized (taskQueue) {
					while (taskQueue.isEmpty() && !dead.get()) {
						try {
							taskQueue.wait();
						} catch (final InterruptedException e) {
							if (dead.get())
								break OUTER;
							Thread.currentThread().interrupt();
						}
					}
					r = taskQueue.remove();
				}
				try {
					r.run();
				} catch (final Throwable t) {
					if (t instanceof InterruptedException && dead.get())
						break;
					t.printStackTrace();
				}
				synchronized (inProcess) {
					inProcess.decrementAndGet();
					inProcess.notifyAll();
				}
			}
		}
	}

	public static int defaultMaxTasks(final int threads) {
		return threads * 100;
	}

	public static int defaultNumberOfThreads() {
		return Runtime.getRuntime().availableProcessors() + 1;
	}

	private final Deque<Runnable> taskQueue = new ArrayDeque<>();
	private final int limit;
	private final boolean fifo;
	private final AtomicBoolean dead = new AtomicBoolean(false);
	private final AtomicBoolean flushing = new AtomicBoolean(false);

	private final Deque<PuddleThread> puddle;

	private final AtomicInteger inProcess = new AtomicInteger(0);

	/**
	 * Creates a puddle with one more threads than there are cores available on
	 * the current machine and a task queue that can hold 100 times this many
	 * tasks.
	 */
	public ThreadPuddle() {
		this(defaultNumberOfThreads());
	}

	/**
	 * Like {@code #ThreadPuddle()}, but with control over FIFO semantics of
	 * work queue.
	 * 
	 * @param fifo
	 *            whether to use first-in-first-out semantics
	 */
	public ThreadPuddle(final boolean fifo) {
		this(defaultNumberOfThreads(), fifo);
	}

	/**
	 * Creates a puddle with the given number of threads and a task queue that
	 * can hold 100 times this many tasks. The work queue will be
	 * first-in-first-out.
	 *
	 * @param threads
	 *            the number of threads in the puddle
	 */
	public ThreadPuddle(final int threads) {
		this(threads, defaultMaxTasks(threads), true);
	}

	/**
	 * Like {@link #ThreadPuddle(int)}, but providing control over FIFO
	 * semantics of work queue.
	 * 
	 * @param threads
	 *            number of threads to use
	 * @param fifo
	 *            whether the work queue should use first-in-first-out semantics
	 */
	public ThreadPuddle(final int threads, final boolean fifo) {
		this(threads, defaultMaxTasks(threads), fifo);
	}

	/**
	 * Creates a puddle with the given number of threads and maximum work queue
	 * size, and first-in-first-out semantics for the work queue.
	 * 
	 * @param threads
	 * @param maxTasks
	 */
	public ThreadPuddle(final int threads, final int maxTasks) {
		this(threads, maxTasks, true);
	}

	/**
	 * With the given number of threads, a task queue of the given size, and
	 * given first-in-first-out semantics for the work queue.
	 *
	 * @param threads
	 *            the number of threads in the puddle
	 * @param maxTasks
	 *            the number of tasks awaiting execution that the puddle can
	 *            hold before {@link #run(Runnable)} blocks until more space is
	 *            available.
	 * @param fifo
	 *            whether to use a first-in-first-out work queue
	 */
	public ThreadPuddle(final int threads, final int maxTasks, final boolean fifo) {
		if (threads < 1)
			throw new ThreadPuddleException("thread count must be positive");
		if (maxTasks < threads)
			throw new ThreadPuddleException("maxTasks must be greater than or equal to thread count");
		puddle = new ArrayDeque<>(threads);
		limit = maxTasks;
		this.fifo = fifo;
		for (int i = 0; i < threads; i++) {
			puddle.add(new PuddleThread());
		}
	}

	/**
	 * Interrupt all threads and mark the puddle as dead. A dead puddle cannot
	 * be used further as all of its threads will be interrupted.
	 */
	public void die() {
		dead.set(true);
		for (final PuddleThread pt : puddle)
			pt.interrupt();
	}

	/**
	 * Forces everything to wait while enqueued tasks are completed. Used to
	 * ensure the products of concurrent processing are complete before moving
	 * on to the next step in the algorithm.
	 */
	public void flush() {
		flushing.set(true);
		synchronized (inProcess) {
			while (inProcess.get() > 0) {
				try {
					// wakes up once a second to facilitate debugging
					inProcess.wait(1000);
				} catch (final InterruptedException e) {
					if (dead.get())
						return;
					Thread.currentThread().interrupt();
				}
			}
		}
		synchronized (flushing) {
			flushing.set(false);
			flushing.notifyAll();
		}
	}

	/**
	 * Get the priority of all the threads in the puddle. See
	 * {@link Thread#getPriority()}.
	 *
	 * @return thread priority
	 */
	public int getPriority() {
		if (dead.get())
			throw new ThreadPuddleException("puddle is dead!");
		return puddle.getFirst().getPriority();
	}

	/**
	 * Submits a task to the puddle.
	 * 
	 * @param r
	 *            task
	 */
	public void run(final Runnable r) {
		run(r, fifo);
	}

	/**
	 * Submits a task to the puddle.
	 *
	 * @param r
	 *            the task to run
	 * @param fifo
	 *            whether to enqueue the work at the end of the queue
	 *            ({@code true}), or beginning ({@code false})
	 */
	public void run(final Runnable r, final boolean fifo) {
		if (dead.get())
			throw new ThreadPuddleException("puddle is dead!");
		synchronized (flushing) {
			while (flushing.get()) {
				try {
					flushing.wait();
				} catch (final InterruptedException e) {
					if (dead.get())
						return;
					Thread.currentThread().interrupt();
				}
			}
		}
		synchronized (inProcess) {
			while (inProcess.get() == limit) {
				try {
					inProcess.wait();
				} catch (final InterruptedException e) {
					if (dead.get())
						return;
					Thread.currentThread().interrupt();
				}
			}
			inProcess.incrementAndGet();
			synchronized (taskQueue) {
				if (fifo)
					taskQueue.addLast(r);
				else
					taskQueue.addFirst(r);
				taskQueue.notifyAll();
			}
			inProcess.notifyAll();
		}
	}

	/**
	 * Set the priority for all threads in the puddle. See
	 * {@link Thread#setPriority(int)}.
	 *
	 * @param priority
	 *            thread priority
	 */
	public void setPriority(final int priority) {
		if (dead.get())
			throw new ThreadPuddleException("puddle is dead!");
		for (final Thread t : puddle)
			t.setPriority(priority);
	}
}
