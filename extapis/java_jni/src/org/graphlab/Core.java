package org.graphlab;

import java.util.Set;

import org.apache.log4j.Logger;
import org.graphlab.data.Vertex;
import org.jgrapht.Graph;

/*
 * This interfaces with the C++ library via JNI and
 * mirrors graphlab::core.
 */

/**
 * Core engine.
 * 
 * <p>Refer to the classes in {@link org.graphlab.demo} for examples on how
 * to use GraphLab. A tutorial is also available on the official website.</p>
 * 
 * <h3>Usage</h3>
 * <p>In general, your applications should be structured as follows:</p>
 * <pre>
Core c = new Core();
c.setGraph(graph);
c.scheduleAll(...);
c.destroy();
 * </pre>
 * <b>Important</b>
 * <p>All applications must invoke {@link #destroy} at the end of its execution;
 * this terminates remaining threads and terminates the application.</p>
 * 
 * <h3>Logging</h3>
 * <p>All logging from the GraphLab Core is done via {@link org.apache.log4j.Logger}.
 * To configure, retrieve the logger using <tt>Logger.getClass(Core.class)</tt>.
 * At the very least, you will need to invoke <code>BasicConfigurator.configure()</code>
 * at the beginning of your code to setup a basic configuration of log4j.</p>
 * 
 * @author Jiunn Haur Lim <jiunnhal@cmu.edu>
 * @see <a href="http://logging.apache.org/log4j/1.2/manual.html">Log4J</a>
 */
public final class Core {

	/** Logger (Java layer only, the C++ layer has its own logger) */
	private static final Logger logger = Logger.getLogger (Core.class);

	/** Indicates if the core has been destroyed and cannot be reused. */
	private boolean mDestroyed = false;

	/** Address of graphlab::core object */
	private long mCorePtr;

	static {
		// load the JNI library
		System.loadLibrary("graphlabjni");
		logger.trace ("JNI library libgraphlabjni loaded.");
	}

	/**
	 * Creates a new GraphLab core with the default configuration options.
	 * 
	 * <p><b>Call {@link #destroy()} when done to free up resources.</b>
	 * The program will not terminate if you forget, because that leaves
	 * child threads alive.</p>
	 * 
	 * @throws CoreException if there was an error creating the core
	 */
	public Core() throws CoreException {

		mCorePtr = createCore();
		if (0 >= mCorePtr)
			throw new CoreException("Unable to create a core.");
		logger.trace ("Core created.");

	}
	
	 /**
   * Creates a new GraphLab core the specified configuration options.
   * 
   * <p><b>Call {@link #destroy()} when done to free up resources.</b>
   * The program will not terminate if you forget, because that leaves
   * child threads alive.</p>
   * 
   * @param config
   *          configuration e.g. scheduler, scope
   * @throws CoreException
   *          if there was an error creating the core
   */
  public Core(CoreConfiguration config) throws CoreException {

    mCorePtr = createCore(config.toString());
    if (0 >= mCorePtr)
      throw new CoreException("Unable to create a core.");
    logger.trace ("Core created.");

  }

  /**
   * Tells the GraphLab core to operate on this graph. This creates a proxy
   * graph in the GraphLab engine. Updates are forwarded to the Java updaters
   * that are scheduled using {@link #schedule(Vertex, Updater)}.
   * 
   * <p><b>Once this is called, your graph must not be modified.</b></p>
   * 
   * @param graph
   *          the graph for the core operate on.
   * @param <G>
   *          Graph type must implement {@link org.jgrapht.DirectedGraph}
   * @param <V>
   *          Vertex type must implement {@link org.graphlab.data.Vertex}.
   * 
   * @throws NullPointerException
   *           if graph is null
   * @throws IllegalArgumentException
   *           if graph is empty
   * @throws IllegalStateException
   *           if {@link #destroy()} was already invoked on this object
   */
	public <G extends Graph<V, E>, V extends Vertex, E>
	void setGraph(G graph) {

		if (null == graph)
			throw new NullPointerException("graph must not be null.");
		
		if (mDestroyed)
			throw new IllegalStateException("Core has been destroyed and may not be reused.");
		
		// inspect vertices
		Set<? extends Vertex> vertices = graph.vertexSet();
		if (null == vertices || 0 == vertices.size())
			throw new IllegalArgumentException("graph must not be empty.");
		
		long startTime = System.currentTimeMillis();

		// add vertices
		for (Vertex vertex : vertices)
		  addVertex(mCorePtr, vertex, vertex.id());

		// add edges
		for (E edge : graph.edgeSet())
		  addEdge(mCorePtr, graph.getEdgeSource(edge).id(), graph.getEdgeTarget(edge).id(), edge);

		long elapsed = System.currentTimeMillis() - startTime;
		logger.info ("Graph transferring took: " + elapsed + " ms.");

	}

  /**
   * Destroys the GraphLab core. Once destroyed, this object may not be used.
   * Calling {@link #destroy()} more than once on the same object will generate
   * a warning in the logs but will not have any other effects.
   */
	public void destroy() {

		if (mDestroyed) {
			logger.warn("Core has already been destroyed and may not be destroyed again.");
			return;
		}

		if (0 == mCorePtr)
			throw new IllegalStateException("Core missing or was never allocated.");

		destroyCore(mCorePtr);
		mDestroyed = true;
		logger.trace("Core destroyed.");

	}

  /**
   * Runs the engine until a termination condition is reached or there are no
   * more tasks remaining to execute.
   * 
   * <p>
   * <b>{@link #setGraph} must be called before invoking this method</b>.
   * </p>
   * 
   * @return amount of time taken in seconds
   * @throws IllegalStateException
   *           if {@link #destroy()} was invoked on this object
   */
	public double start() {
		
		if (mDestroyed)
			throw new IllegalStateException("Core has been destroyed and may not be reused.");
		
		logger.info("GraphLab engine started.");
		return start(mCorePtr);
		
	}
	
	/**
	 * Gets the number of updates executed by the engine.
	 * @return update count
   * @throws IllegalStateException
   *           if {@link #destroy()} was invoked on this object
	 */
	public long lastUpdateCount(){
	  
	  if (mDestroyed)
      throw new IllegalStateException("Core has been destroyed and may not be reused.");
	  
	  return lastUpdateCount(mCorePtr);
	  
	}

	/**
	 * Adds a global constant entry.
	 * @param <Type>
	 *           type of object to add
	 * @param key
	 *           key to uniquely identify the object
	 * @param object
	 *           the object to add
	 */
  public <Type> void addGlobalConst(String key, Type object) {
    addGlobalConst(mCorePtr, key, object);
  }
  
  /**
   * Adds a global entry.
   * @param <Type>
   *           type of object to add
   * @param key
   *           key to uniquely identify the object
   * @param object
   *           the object to add
   */
  public <Type> void addGlobal(String key, Type object){
    addGlobal(mCorePtr, key, object);
  }

  /**
   * Retrieves a global entry.
   * @param <Type>
   *          type of object to retrieve (must be the same as the
   *          type that was saved.)
   * @param key
   *          key to uniquely identify the object
   * @param cls
   *          class of the object to retrieve
   * @return global object
   */
  public <Type> Type getGlobal(String key, Class<Type> cls) {
    Object obj = getGlobal(mCorePtr, key);
    return cls.cast(obj);
  }
  
  public <Type> void setGlobal(String key, Type object){
    setGlobal(mCorePtr, key, object);
  }

  /**
   * Schedules the execution of an updater on the specified vertex.
   * 
   * <p>The GraphLab schedulers will execute the update on the vertex when
   * it is safe to do so (as defined by the consistency model.)</p>
   * 
   * @param vertex
   *          vertex to schedule
   * @param updater
   *          updater to execute
   * @throws NullPointerException
   *           if <tt>updater</tt> or <tt>vertex</tt> was null.
   * @throws IllegalStateException
   *           if {@link #destroy()} was invoked on this object
   */
	public void schedule(Vertex vertex, Updater<?, ?, ?> updater) {

		if (null == updater || null == vertex)
			throw new NullPointerException("updater and vertex must not be null.");
		
		if (mDestroyed)
			throw new IllegalStateException("Core has been destroyed and may not be reused.");

		schedule(mCorePtr, updater, vertex.id());
		
	}
	
  /**
   * Schedules the execution of the update function on all vertices.
   * 
   * <p>
   * The GraphLab schedulers will execute the updates it is safe to do so (as
   * defined by the consistency model.)
   * </p>
   * 
   * @param updater
   *          updater to execute
   * @throws NullPointerException
   *           if <tt>updater</tt> was null
   * @throws IllegalStateException
   *           if {@link #destroy()} was invoked on this object
   */
	public void scheduleAll(Updater<?, ?, ?> updater){
	  
	  if (null == updater)
	    throw new NullPointerException("updater must not be null.");
	  
	  if (mDestroyed)
	    throw new IllegalStateException("Core has been destroyed and may not be reused.");

	  scheduleAll(mCorePtr, updater);
	  
	}
	
  /**
   * Registers an aggregator with the engine. The aggregator is used to collect
   * data about the graph every "interval" updates.
   * 
   * @param key
   *          the name of the aggregator
   * @param aggregator
   *          the initial value of the aggregator
   * @param interval
   *          the frequency at which the aggregator is initiated. corresponds
   *          approximately to the number of update function calls before the
   *          sync is reevaluated. If 0 the aggregator is not run automatically.
   * @throws NullPointerException
   *          if <tt>key</tt> or <tt>aggregator</tt> is null
   * @throws IllegalArgumentException
   *          if <tt>key</tt> has length 0
   */
	public void addAggregator(String key, Aggregator<?, ?> aggregator, long interval){
	  if (null == key || null == aggregator)
	    throw new NullPointerException ("key and aggregator may not be null.");
	  if (key.length() <= 0)
	    throw new IllegalArgumentException ("key must have length of at least 1.");
	  addAggregator(mCorePtr, key, aggregator, interval);
	}
	
  /**
   * Executes the specified aggregator immediately.
   * 
   * @param key
   *          name of the aggregator to execute
   * @throws NullPointerException
   *          if <tt>key</tt> is null
   * @throws IllegalArgumentException
   *          if <tt>key</tt> has length 0
   */
	public void aggregateNow(String key){
	  if (null == key)
	    throw new NullPointerException ("key must not be null.");
	  if (key.length() <= 0)
	    throw new IllegalArgumentException ("key must have length of at least 1.");
	  aggregateNow(mCorePtr, key);
	}
	
  /**
   * Sets the number of CPUs that the engine will use. This will destroy the
   * current engine and any tasks associated with the current scheduler.
   * 
   * <p>Using {@link #Core(CoreConfiguration)} to configure the core is preferred
   * over using this method.</p>
   * 
   * @param ncpus
   *          number of CPUs that the engine will use.
   * 
   * @throws IllegalArgumentException
   *           if <tt>ncpus</tt> was negative.
   * @throws IllegalStateException
   *           if {@link #destroy()} has already been invoked on this object
   */
	public void setNCpus(long ncpus){
	  if (0 >= ncpus)
	    throw new IllegalArgumentException ("ncpus must be positive.");
	  if (mDestroyed)
      throw new IllegalStateException("Core has been destroyed and may not be reused.");
	  setNCpus(mCorePtr, ncpus);
	}
	
  /**
   * Sets the type of scheduler. This only sets the type, and ignores any
   * scheduler options. This will destroy the current engine and any tasks
   * currently associated with the scheduler.
   * 
   * <p>Using {@link #Core(CoreConfiguration)} to configure the core is preferred
   * over using this method.</p>
   * 
   * @param scheduler
   * @throws NullPointerException
   *           if <tt>scheduler</tt> was null
   * @throws IllegalStateException
   *           if {@link #destroy()} has already been invoked on this object
   */
  public void setSchedulerType(Scheduler scheduler) {
    if (null == scheduler)
      throw new NullPointerException("scheduler must not be null.");
    if (mDestroyed)
      throw new IllegalStateException("Core has been destroyed and may not be reused.");
    setSchedulerType(mCorePtr, scheduler.type());
  }
	
  /**
   * Sets the scope consistency model that the engine will use. This will
   * destroy the current engine and any tasks currently associated with the
   * scheduler.
   * 
   * <p>Using {@link #Core(CoreConfiguration)} to configure the core is preferred
   * over using this method.</p>
   * 
   * @param scope
   * @throws NullPointerException
   *           if <tt>scope</tt> was null
   * @throws IllegalStateException
   *           if {@link #destroy()} has already been invoked on this object
   */
	public void setScopeType(Scope scope){
	  if (null == scope)
	    throw new NullPointerException("scope must not be null.");
	  if (mDestroyed)
	    throw new IllegalStateException("Core has been destroyed and may not be reused.");
	  setScopeType(mCorePtr, scope.toString());
	}
	
  /**
   * Creates and initializes graphlab::core &raquo; dynamically allocates a
   * core. Must be freed by a corresponding call to {@link #destroyCore()}.
   * 
   * @return address of core or 0 on failure
   */
	private native long createCore();

  /**
   * Creates and initializes graphlab::core &raquo; dynamically allocates a
   * core. Must be freed by a corresponding call to {@link #destroyCore()}.
   * 
   * @return address of core or 0 on failure
   */
	private native long createCore(String command_line_args);

  /**
	 * Deletes the graphlab::core that was allocated in {@link #initCore()}.
	 * 
	 * @param ptr
	 *        {@link #mCorePtr}
	 */
	private native void destroyCore(long ptr);
	
	/**
	 * Add additional vertices up to <tt>n</tt>. This will fail if resizing down. 
	 * @param ptr
	 * 			{@link #mCorePtr}
	 * @param n
	 * 			number of vertices to add
	 */
	private native void resizeGraph(long ptr, int n);
	
	/**
	 * Adds a vertex to the native graph.
	 * @param ptr
	 * 			{@link #mCorePtr}
	 * @param vertex
	 * 			vertex
	 * @param vertexID
	 *       id of vertex
	 */
	private native void addVertex(long ptr, Vertex vertex, int vertexID);

	/**
	 * Adds an edge to the native graph.
	 * @param ptr
	 * 			{@link #mCorePtr}
	 * @param source
	 * 			graphlab vertex ID
	 * @param target
	 * 			graphlab vertex ID
	 */
	private native <E> void addEdge(long ptr, int source, int target, E edge);
	
	/**
	 * Add a single update function to a single vertex
	 * @param core_ptr
	 *       {@link #mCorePtr}
	 * @param updater
	 * @param vertexId
	 *       graphlab vertex ID
	 */
	private native void schedule(long core_ptr, Updater<?, ?, ?> updater, int vertexId);

	/**
	 * Add the given function to all vertices using the given priority
	 * @param core_ptr
	 *       {@link #mCorePtr}
   * @param updater
	 */
	private native void scheduleAll(long core_ptr, Updater<?, ?, ?> updater);
	
  /**
   * Run the engine until a termination condition is reached or there are no
   * more tasks remaining to execute.
   * @param ptr
   *      {@link #mCorePtr}
   * @return runtime
   */
	private native double start(long ptr);
	
	/**
	 * Gets the number of updates executed by the engine.
	 * @param ptr {@link Core#mCorePtr}
	 * @return update count
	 */
	private native long lastUpdateCount(long ptr);
	
	/**
	 * Add a global constant entry
	 * @param ptr
	 *       {@link mCorePtr}
	 * @param key
	 * @param obj
	 */
	private native void addGlobalConst(long ptr, String key, Object obj);
	
	/**
   * Adds a global entry
   * @param ptr
   *       {@link mCorePtr}
   * @param key
   * @param obj
   */
	private native void addGlobal(long ptr, String key, Object obj);
	
	/**
	 * Retrieves a global entry
	 * @param ptr
	 *       {@link mCorePtr}
	 * @param key
	 * @return global entry
	 */
	private native Object getGlobal(long ptr, String key);
	
	/**
	 * Updates the value of a global entry
	 * @param ptr
	 *       {@link mCorePtr}
	 * @param key
	 * @param obj
	 */
	private native void setGlobal(long ptr, String key, Object obj);
	
  /**
   * Registers a aggregator with the engine. The aggregator is used to collect
   * data about the graph every "interval" updates.
   * 
   * @param ptr
   *      {@link mCorePtr}
   * @param key
   * @param aggregator
   * @param interval
   */
	private native void addAggregator
	  (long ptr, String key, Aggregator<?, ?> aggregator, long interval);
	
  /**
   * Performs a sync immediately. This function requires that the shared
   * variable already be registered with the engine.
   * 
   * @param ptr
   *      {@link mCorePtr}
   * @param key
   */
	private native void aggregateNow(long ptr, String key);

	/**
	 * Set the number of CPUs that the engine will use.
	 * This will destroy the current engine and any tasks associated with the current scheduler.
	 * If this is not what you want, then configure the core in the constructor instead.
	 * 
	 * @param ptr
	 *       {@link #mCorePtr}
	 * @param ncpus
	 *       number of CPUs
	 */
	private native void setNCpus(long ptr, long ncpus);
	
	/**
	 * Set the type of scheduler. 
	 * This will destroy the current engine and any tasks currently associated with the scheduler.
	 * If this is not what you want, then configure the core in the constructor instead.
	 * 
	 * @param ptr
	 *       {@link #mCorePtr}
	 * @param schedulerType
	 */
	private native void setSchedulerType(long ptr, String schedulerType);
	
	/**
	 * Set the scope consistency model used in this engine. 
	 * This will destroy the current engine and any tasks associated with the current scheduler.
	 * If this is not what you want, then configure the core in the constructor instead.
	 * 
	 * @param ptr
	 *       {@link #mCorePtr}
	 * @param scopeType
	 */
	private native void setScopeType(long ptr, String scopeType);
	
	/**
	 * Generic exception for dealing with GraphLab cores. Usually thrown when an error
	 * is encountered while trying to allocate memory for the core.
	 * 
	 * @author Jiunn Haur Lim
	 */
	public static class CoreException extends Exception {

		private static final long serialVersionUID = -1231034883125084972L;

		public CoreException(String string) {
			super(string);
		}

	}

}
