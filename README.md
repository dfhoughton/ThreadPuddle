ThreadPuddle
============

David F. Houghton
31 August 2013

This is a very small, simple library providing concurrency with minimal effort.

Benchmarks
----------

Here are some benchmarks, ThreadPuddle vs. ThreadPool

*Code*

    import dfh.thread.ThreadPuddle;
    import dfh.thread.pool.ThreadPool;
    
    /**
     * Which is faster, ThreadPool or ThreadPuddle?
     * 
     * @author David F. Houghton
     */
    public class Benchmarks {
    
    	/**
    	 * Optionally takes a pair of integers: the number of warmup iterations and
    	 * the number of benchmark iterations
    	 * 
    	 * @param args
    	 */
    	public static void main(String[] args) {
    		
    		// set parameters
    		int warmup, benchmark;
    		if (args.length > 1) {
    			warmup = Integer.parseInt(args[0]);
    			benchmark = Integer.parseInt(args[1]);
    		} else {
    			warmup = 1000000;
    			benchmark = 10 * warmup;
    			System.out.println("using default warmup and benchmark iterations");
    		}
    		System.out.printf("warmup: %d; benchmark: %d%n", warmup, benchmark);
    
    		ThreadPuddle puddle = new ThreadPuddle();
    
    		// warm up puddle
    		for (int i = 0; i < warmup; i++) {
    			final int j = i;
    			puddle.run(new Runnable() {
    
    				@Override
    				public void run() {
    					@SuppressWarnings("unused")
    					int k = j * 2;
    				}
    			});
    		}
    		puddle.flush();
    
    		// time puddle
    		long t1 = System.currentTimeMillis();
    		for (int i = 0; i < benchmark; i++) {
    			final int j = i;
    			puddle.run(new Runnable() {
    
    				@Override
    				public void run() {
    					@SuppressWarnings("unused")
    					int k = j * 2;
    				}
    			});
    		}
    		puddle.flush();
    		long t2 = System.currentTimeMillis();
    
    		// warm up ThreadPool
    		for (int i = 0; i < warmup; i++) {
    			final int j = i;
    			ThreadPool.enqueue(new Runnable() {
    
    				@Override
    				public void run() {
    					@SuppressWarnings("unused")
    					int k = j * 2;
    				}
    			});
    		}
    		ThreadPool.flush();
    
    		// time ThreadPool
    		long t3 = System.currentTimeMillis();
    		for (int i = 0; i < benchmark; i++) {
    			final int j = i;
    			ThreadPool.enqueue(new Runnable() {
    
    				@Override
    				public void run() {
    					@SuppressWarnings("unused")
    					int k = j * 2;
    				}
    			});
    		}
    		ThreadPool.flush();
    		long t4 = System.currentTimeMillis();
    
    		// results
    		System.out.printf("ThreadPuddle milliseconds: %d%n", t2 - t1);
    		System.out.printf("ThreadPool milliseconds: %d%n", t4 - t3);
    		System.out.printf("ratio puddle:pool : %.2f", (t4 - t3)
    				/ (double) (t2 - t1));
    	}
    
    }

*Results*

    using default warmup and benchmark iterations
    warmup: 1000000; benchmark: 10000000
    ThreadPuddle milliseconds: 10672
    ThreadPool milliseconds: 29685
    ratio pool:puddle : 2.78

TODO

Create build.xml file to create javadocs, compile code, run the tests, and pack
this wee bit of code into a jar.

Other Stuff
-----------
This software is distributed under the terms of the FSF Lesser Gnu 
Public License (see lgpl.txt).
