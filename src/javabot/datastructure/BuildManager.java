package javabot.datastructure;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Queue;

import javabot.gamestructure.DebugEngine;
import javabot.gamestructure.DebugModule;
import javabot.gamestructure.GameHandler;
import javabot.model.Unit;
import javabot.types.UnitType;
import javabot.types.UnitType.UnitTypes;
import javabot.util.BWColor;

public class BuildManager {
	private GameHandler game;
	private Queue<BuildingPlan> buildingQueue;

	public BuildManager(GameHandler igame) {
		game = igame;
		buildingQueue = new ArrayDeque<BuildingPlan>();

		game.registerDebugFunction(new DebugModule() {
			@Override
			public void draw(DebugEngine engine) {
				for (BuildingPlan plan : buildingQueue) {
					int x = plan.getTx() * 32;
					int y = plan.getTy() * 32;
					int width = game.getUnitType(plan.getTypeID())
							.getTileWidth() * 32;
					int height = game.getUnitType(plan.getTypeID())
							.getTileHeight() * 32;
					game.drawBox(x, y, x + width, y + height, BWColor.GREEN,
							false, false);
				}
				BuildingPlan nextBuilding = getToBuild();
				game.drawText(5, 20, "Building Queue: "
						+ (nextBuilding != null ? nextBuilding.toString()
								: "None"), true);
			}
		});
	}

	public void addBuilding(BuildingPlan buildingType) {
		buildingQueue.add(buildingType);
	}

	public void addBuilding(int tx, int ty, UnitTypes type) {
		addBuilding(new BuildingPlan(tx, ty, type));
	}

	public void addBuilding(Point buildLocation, UnitTypes type) {
		addBuilding(buildLocation.x, buildLocation.y, type);
	}

	public void addResearch() {

	}

	public void addUnit() {

	}

	public BuildingPlan getToBuild() {
		return buildingQueue.peek();
	}

	// Call this whenever a building completes contruction
	public void doneBuilding(Unit u) {
		// Go through all the planned buildings
		for (BuildingPlan p : buildingQueue) {
			// If it's the right building according to the plan
			if (u.getTypeID() == p.getTypeID() && u.getTileX() == p.getTx()
					&& u.getTileY() == p.getTy()) {
				// It has been completed
				buildingQueue.remove(p);
			}
		}
	}

	public void getToResearch() {

	}

	public void getToTrain() {

	}
}
