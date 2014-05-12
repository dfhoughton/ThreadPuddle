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
import java.util.LinkedList;
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
			while (true) {
				synchronized (taskQueue) {
					while (taskQueue.isEmpty() && !interrupted()) {
						try {
							taskQueue.wait();
						} catch (InterruptedException e) {
						}
					}
					if (interrupted())
						break;
					r = taskQueue.remove();
				}
				try {
					r.run();
				} catch (Throwable t) {
					t.printStackTrace();
				}
				synchronized (inProcess) {
					inProcess.decrementAndGet();
					inProcess.notifyAll();
				}
			}
		}
	}

	private final LinkedList<Runnable> taskQueue = new LinkedList<Runnable>();
	private final int limit;
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
		this(Runtime.getRuntime().availableProcessors() + 1);
	}

	/**
	 * Creates a puddle with the given number of threads and a task queue that
	 * can hold 100 times this many tasks.
	 * 
	 * @param threads
	 *            the number of threads in the puddle
	 */
	public ThreadPuddle(int threads) {
		this(threads, threads * 100);
	}

	/**
	 * With the given number of threads and a task queue of the given size.
	 * 
	 * @param threads
	 *            the number of threads in the puddle
	 * @param maxTasks
	 *            the number of tasks awaiting execution that the puddle can
	 *            hold before {@link #run(Runnable)} blocks until more space is
	 *            available.
	 */
	public ThreadPuddle(int threads, int maxTasks) {
		if (threads < 1)
			throw new ThreadPuddleException("thread count must be positive");
		if (maxTasks < threads)
			throw new ThreadPuddleException(
					"maxTasks must be greater than or equal to thread count");
		puddle = new ArrayDeque<>(threads);
		limit = maxTasks;
		for (int i = 0; i < threads; i++) {
			puddle.add(new PuddleThread());
		}
	}

	/**
	 * Submits a task to the puddle.
	 * 
	 * @param r
	 *            the task to run
	 */
	public void run(Runnable r) {
		if (dead.get())
			throw new ThreadPuddleException("puddle is dead!");
		synchronized (flushing) {
			while (flushing.get()) {
				try {
					flushing.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		synchronized (inProcess) {
			while (inProcess.get() == limit) {
				try {
					inProcess.wait();
				} catch (InterruptedException e) {
					return;
				}
			}
			inProcess.incrementAndGet();
			synchronized (taskQueue) {
				taskQueue.add(r);
				taskQueue.notifyAll();
			}
			inProcess.notifyAll();
		}
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
				} catch (InterruptedException e) {
				}
			}
		}
		synchronized (flushing) {
			flushing.set(false);
			flushing.notifyAll();
		}
	}

	/**
	 * Interrupt all threads and mark the puddle as dead. A dead puddle cannot
	 * be used further as all of its threads will be interrupted.
	 */
	public void die() {
		dead.set(true);
		for (PuddleThread pt : puddle)
			pt.interrupt();
	}

	/**
	 * Set the priority for all threads in the puddle. See
	 * {@link Thread#setPriority(int)}.
	 * 
	 * @param priority
	 *            thread priority
	 */
	public void setPriority(int priority) {
		if (dead.get())
			throw new ThreadPuddleException("puddle is dead!");
		for (Thread t : puddle)
			t.setPriority(priority);
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
}
