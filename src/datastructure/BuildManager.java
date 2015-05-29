package datastructure;

import gamestructure.GameHandler;
import gamestructure.debug.DebugEngine;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Queue;

import bwapi.Color;
import bwapi.Unit;
import bwapi.UnitType;

public class BuildManager {
	public static Hashtable<UnitType, Integer> unitMinimums;
	public static Queue<BuildingPlan> buildingQueue;
	public static Queue<UnitType> unitQueue;

	static {
		unitMinimums = new Hashtable<UnitType, Integer>();
		buildingQueue = new ArrayDeque<BuildingPlan>();
		unitQueue = new ArrayDeque<UnitType>();

		registerDebugFunctions();
	}

	/** This constructor should never be called */
	private BuildManager() {
	}

	public static void addToQueue(UnitType unitType) {
		if (unitType == UnitType.Terran_Refinery) {
			// Refineries get special treatment!
			for (Base b : BaseManager.getMyBases()) {
				// Find a gas that isn't taken yet
				for (GasResource r : b.gas) {
					if (!r.gasTaken()) {
						addBuilding(r.getX() / 32 - 2, r.getY() / 32 - 1,
								UnitType.Terran_Refinery);
						break;
					}
				}
				GameHandler
						.sendText("Wanted to take another gas, but none left!"); // TODO
			}
		} else if (unitType.isBuilding()) {
			// Otherwise, buildings
			if (BaseManager.main != null) {
				Point location = getBuildLocation(BaseManager.main.getX(),
						BaseManager.main.getY(), unitType);
				addBuilding(location.x, location.y, unitType);
			}
		} else {
			// Finally, units
			unitQueue.add(unitType);
		}
	}

	// Add multiple units at once
	public static void addToQueue(UnitType unitType, int count) {
		for (int i = 0; i < count; i++) {
			addToQueue(unitType);
		}
	}

	// Build a building at a specific location
	public static void addBuilding(Point buildLocation, UnitType type) {
		addBuilding(buildLocation.x, buildLocation.y, type);
	}

	// Build a building at a specific location
	public static void addBuilding(int tx, int ty, UnitType type) {
		buildingQueue.add(new BuildingPlan(tx, ty, type));
	}

	// TODO put this somewhere else?
	// Finds a nearby valid build location for the building of specified type
	// Returns the Point object representing the suitable build tile
	// position
	// for a given building type near specified pixel position (or
	// Point(-1,-1) if not found)
	private static Point getBuildLocation(int x, int y, UnitType toBuild) {
		Point ret = new Point(-1, -1);
		int maxDist = 3;
		int stopDist = 40;
		int tileX = x / 32;
		int tileY = y / 32;

		while ((maxDist < stopDist) && (ret.x == -1)) {
			for (int i = tileX - maxDist; i <= tileX + maxDist; i++) {
				for (int j = tileY - maxDist; j <= tileY + maxDist; j++) {
					if (canBuildHere(i, j, toBuild)) {
						// units that are blocking the tile
						boolean unitsInWay = false;
						for (Unit u : GameHandler.getAllUnits()) {
							if ((Math.abs(u.getX() / 32 - i) < 4)
									&& (Math.abs(u.getY() / 32 - j) < 4)) {
								unitsInWay = true;
							}
						}
						if (!unitsInWay) {
							ret.x = i;
							ret.y = j;
							return ret;
						}
					}
				}
			}
			maxDist++;
		}

		if (ret.x == -1) {
			throw new NullPointerException();
		}

		return ret;
	}

	// Checks if the building type specified can be built at the coordinates
	// given
	private static boolean canBuildHere(int left, int top, UnitType type) {
		int width = type.tileWidth();
		int height = type.tileHeight();

		// Check if location is buildable
		for (int i = left; i < left + width - 1; i++) {
			for (int j = top; j < top + height - 1; j++) {
				if (!(GameHandler.isBuildable(i, j, true))) {
					return false;
				}
			}
		}
		// Check if another building is planned for this spot
		for (BuildingPlan bp : buildingQueue) {
			if (bp.getTx() <= left + width
					&& bp.getTx() + bp.getType().tileWidth() >= left) {
				if (bp.getTy() <= top + height
						&& bp.getTy() + bp.getType().tileHeight() >= top) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Call this whenever a unit completes construction.
	 * 
	 * @param u
	 *            The unit that has just completed.
	 * **/
	public static void buildingComplete(Unit u) {
		UnitType type = u.getType();
		if (!type.isBuilding()) {
			// Go through planned units
			unitQueue.remove(type);
		} else {
			// Go through planned buildings
			for (BuildingPlan p : buildingQueue) {
				// If it's the right building according to the plan
				if (u.getType().equals(p.getType())
						&& u.getTilePosition().equals(p.getTilePosition())) {
					// It has been completed
					buildingQueue.remove(p);

					if (u.getType().isRefinery()) {
						// The gas geyser becomes a refinery...
						BaseManager.refineryComplete(u);
						// If it's a refinery, the worker will automatically
						// become
						// a gas miner!
						p.builder.gather(BaseManager.getResource(u));
					} else {
						// Otherwise, back to work!
						p.builder.gather(p.builder.getCurrentResource());
					}
					break;
				}
			}
		}
	}

	public static boolean isInQueue(UnitType unitType) {
		for (BuildingPlan plan : buildingQueue) {
			if (plan.getType() == unitType) {
				return true;
			}
		}
		for (UnitType unitInQueue : unitQueue) {
			if (unitInQueue == unitType) {
				return true;
			}
		}
		return false;
	}

	public static int getCountInQueue(UnitType unitType) {
		int count = 0;
		for (BuildingPlan plan : buildingQueue) {
			if (plan.getType() == unitType) {
				count++;
			}
		}
		for (UnitType type : unitQueue) {
			if (type == unitType) {
				count++;
			}
		}
		return count;
	}

	public static int getMyUnitCount(UnitType type) {
		int count = 0;
		for (Unit u : GameHandler.getAllUnits()) {
			if (!u.isBeingConstructed() && u.getType() == type) {
				count++;
			}
		}
		return count;
	}

	public static void setMinimum(UnitType unitType, int min) {
		unitMinimums.put(unitType, min);
	}

	/**
	 * Check if any buildings or units are below required minimums. If they are,
	 * put more of them into the building queue!
	 */
	public static void checkMinimums() {
		for (Entry<UnitType, Integer> entry : unitMinimums.entrySet()) {
			UnitType unitType = entry.getKey();
			int currentCount = getMyUnitCount(unitType);
			int inQueueCount = getCountInQueue(unitType);
			int requiredCount = entry.getValue();
			if (currentCount + inQueueCount < requiredCount) {
				GameHandler.sendText("Queuing up another "
						+ unitType.toString());
				addToQueue(unitType);
			}
		}
	}

	public static void registerDebugFunctions() {
		DebugEngine.createDebugModule("buildingqueue").setDraw(
				() -> {
					String buildQueueString = "";
					for (BuildingPlan plan : buildingQueue) {
						int x = plan.getTx() * 32;
						int y = plan.getTy() * 32;
						int width = plan.getType().tileWidth() * 32;
						int height = plan.getType().tileHeight() * 32;
						DebugEngine.drawBoxMap(x, y, x + width, y + height,
								Color.Green, false);
						DebugEngine
								.drawTextMap(x, y, plan.getType().toString());
						if (plan.builder != null) {
							int bx = plan.builder.getX();
							int by = plan.builder.getY();
							DebugEngine.drawLineMap(bx, by, x + width / 2, y
									+ width / 2, Color.Green);
						}

						buildQueueString += plan.toString() + ", ";
					}
					DebugEngine.drawTextScreen(5, 20, "Building Queue: "
							+ buildQueueString);
				});
		DebugEngine.createDebugModule("trainingqueue").setDraw(
				() -> {
					String trainingQueueString = "";
					for (UnitType type : unitQueue.toArray(new UnitType[0])) {
						trainingQueueString += type.toString() + ", ";
					}
					DebugEngine.drawTextScreen(5, 40, "Training Queue: "
							+ trainingQueueString);
				});
		DebugEngine.createDebugModule("unitminimums").setDraw(() -> {
			// Unit minimums
				DebugEngine.drawTextScreen(5, 80,
						"Unit Minimums: current(queued)/required");
				int y = 90;
				for (Entry<UnitType, Integer> entry : unitMinimums.entrySet()) {
					UnitType unitType = entry.getKey();
					int inQueueCount = getCountInQueue(unitType);
					int currentCount = getMyUnitCount(unitType);
					int requiredCount = entry.getValue();

					if (inQueueCount != 0 || currentCount != 0
							|| requiredCount != 0) {
						DebugEngine.drawTextScreen(5, y, unitType.toString()
								+ ": " + currentCount + "(" + inQueueCount
								+ ")/" + requiredCount);
						y += 10;
					}
				}
			});
	}
}
