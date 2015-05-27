package gamestructure;

/**
 * A single module to be used in the {@link DebugEngine}. Modules should be
 * instantiated as abstract inner classes within the class it is intended to
 * debug for. Each module should independently display one pertinent piece of
 * information from its enclosing class.<br>
 * Modules must ultimately be registered to the DebugEngine using
 * {@link DebugEngine#registerDebugModule(DebugModule)}.
 * 
 * @author wesley
 *
 */
public abstract class DebugModule {
	/**
	 * The name of this DebugModule. It must be and can only be set during
	 * construction. This is used when parsing commands to the
	 * {@link #DebugEngine}. This string must be kept in lower case by all
	 * accessor methods.
	 */
	public final String name;
	/**
	 * Basic functionality for all DebugModules is activating and deactivating.
	 * When this is true, the DebugModule will paint. When this is false, it
	 * will not. This is checked in {@link #drawIfActive}.<br>
	 * This is toggled by {@link #onReceiveCommand} by default. This behaviour
	 * can be overridden.
	 */
	private boolean active = false;

	/**
	 * Constructs a DebugModule with the specified name.
	 * 
	 * @param iname
	 *            The name to use when referring to the module.
	 */
	public DebugModule(String iname) {
		name = iname.toLowerCase();
	}

	/**
	 * Draws the DebugModule if and only if it is active by calling
	 * {@link #draw}.
	 * 
	 * @param engine
	 *            The engine to use to draw.
	 * @throws ShapeOverflowException
	 *             If the engine throws a ShapeOverflowException, it propagates
	 *             outward here.
	 */
	void drawIfActive(DebugEngine e) throws ShapeOverflowException {
		if (active) {
			draw(e);
		}
	}

	/**
	 * Draws the debugModule immediately. Should be called from
	 * {@link #drawIfActive}.
	 * 
	 * @param engine
	 *            The engine to use to draw.
	 * @throws ShapeOverflowException
	 *             If the engine throws a ShapeOverflowException, it propagates
	 *             outward here.
	 */
	protected void draw(DebugEngine e) throws ShapeOverflowException {
	}

	/**
	 * This function is called when a command prefixed with the module's name is
	 * received. This function should be overridden for additional control over
	 * commands.
	 * 
	 * @param command
	 *            The line of commands received. The line is separated by
	 *            whitespace into the array.
	 *            <ul>
	 *            <li><b>command[0]</b> is either the module's name or "all".</li>
	 *            </ul>
	 * @param engine
	 *            The engine to use to draw.
	 * @throws ShapeOverflowException
	 *             If the engine throws a ShapeOverflowException, it propagates
	 *             outward here.
	 */
	protected void onReceiveCommand(String[] command, DebugEngine engine)
			throws ShapeOverflowException {
		active = !active;
	}

	/**
	 * The null module is a debugModule designed to absorb any function calls
	 * with no side effects.
	 */
	private static final DebugModule nullModule = new DebugModule("null") {
	};

	/**
	 * Get the singleton instance of nullModule, a debugModule whose functions
	 * can be called without any effects.
	 * 
	 * @return the null debugModule.
	 */
	public static final DebugModule getNullModule() {
		return nullModule;
	}
}
