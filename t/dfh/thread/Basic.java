package dfh.thread;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class Basic {

	@Test
	public void test() {
		try {
			ThreadPuddle puddle = new ThreadPuddle();
			for (int i = 0; i < 1000; i++) {
				final int j = i;
				puddle.run(new Runnable() {

					@Override
					public void run() {
						System.out.println(Math.sqrt(j));
					}
				});
			}
		} catch (Throwable t) {
			fail("Caught error: " + t.getMessage());
		}
	}
	@Test
	public void exceptions() {
		final AtomicInteger ai = new AtomicInteger();
		try {
			ThreadPuddle puddle = new ThreadPuddle();
			for (int i = 0; i < 100; i++) {
				Runnable r = new Runnable() {
					@Override
					public void run() {
						ai.incrementAndGet();
						throw new RuntimeException("oopsie!");
					}
				};
				puddle.run(r);
			}
			puddle.flush();
			org.junit.Assert.assertEquals("incremented every time", 100L, ai.longValue());
		} catch (Exception e) {
			org.junit.Assert.fail("error thrown: " + e.getMessage());
		}
	}

}
