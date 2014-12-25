package eaglesWings.micromanager;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import javabot.model.Unit;
import javabot.types.UnitType;
import javabot.types.UnitType.UnitTypes;
import javabot.types.WeaponType;
import javabot.util.BWColor;
import eaglesWings.datastructure.Base;
import eaglesWings.datastructure.BaseManager;
import eaglesWings.datastructure.BaseStatus;
import eaglesWings.datastructure.Worker;
import eaglesWings.datastructure.WorkerTask;
import eaglesWings.gamestructure.DebugEngine;
import eaglesWings.gamestructure.DebugModule;
import eaglesWings.gamestructure.Debuggable;
import eaglesWings.gamestructure.GameHandler;

public class MicroManager implements Debuggable {

	private GameHandler game;
	private BaseManager baseManager;

	private int mapWidth;
	private int mapHeight;
	private double[][] targetMap;
	private double[][] threatMap;
	private static final long THREAT_MAP_REFRESH_DELAY = 1000;

	private Base scoutingTarget;
	private Unit scoutingUnit;

	private HashMap<UnitTypes, HashMap<Integer, UnitAgent>> units;

	public MicroManager(GameHandler igame, BaseManager ibaseManager) {
		game = igame;
		baseManager = ibaseManager;

		mapWidth = game.getMap().getWidth();
		mapHeight = game.getMap().getHeight();
		targetMap = new double[mapWidth + 1][mapHeight + 1];
		threatMap = new double[mapWidth + 1][mapHeight + 1];

		units = new HashMap<UnitTypes, HashMap<Integer, UnitAgent>>();

		// Update threat map
		new Timer().scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				// Reset target and threat counter
				for (int x = 0; x < mapWidth; x++) {
					for (int y = 0; y < mapHeight; y++) {
						targetMap[x][y] = 0;
						threatMap[x][y] = 0;
					}
				}

				// Count the targets and threats
				for (Unit u : game.getEnemyUnits()) {
					UnitType unitType = game.getUnitType(u.getTypeID());
					// Get the x and y grid point coordinates
					int x = u.getX() / 32;
					int y = u.getY() / 32;
					// Get the ground weapon's range
					WeaponType weapon = game.getWeaponType(unitType
							.getGroundWeaponID());
					double radius = weapon.getMaxRange() / 32 + 2;
					double target;
					if (unitType.isWorker()) {
						target = 100;
					} else {
						target = 0;
					}
					double threat = weapon.getDamageAmount();

					ArrayList<Point> threatPoints = generateCircleCoordinates(
							x, y, radius);
					for (Point p : threatPoints) {
						targetMap[p.x][p.y] += target * (10 - p.distance(x, y))
								/ 10;
						threatMap[p.x][p.y] += threat
								* (radius - p.distance(x, y)) / radius;
					}
				}
			}

			private ArrayList<Point> generateCircleCoordinates(int cx, int cy,
					double r) {
				ArrayList<Point> points = new ArrayList<Point>();
				for (int x = (int) Math.floor(-r); x < r; x++) {
					int y1 = (int) Math.round(Math.sqrt(Math.pow(r, 2)
							- Math.pow(x, 2)));
					int y2 = -y1;
					for (int y = y2; y < y1; y++) {
						if (x + cx > 0 && x + cx < mapWidth && y + cy > 0
								&& y + cy < mapHeight) {
							points.add(new Point(x + cx, y + cy));
						}
					}
				}
				return points;
			}
		}, 0, THREAT_MAP_REFRESH_DELAY);
	}

	public void act() {
		// Move scouting unit(s)
		scout();

		// Manage the rest of the units
		for (Entry<UnitTypes, HashMap<Integer, UnitAgent>> unitTypeMap : units
				.entrySet()) {
			for (Entry<Integer, UnitAgent> entry : unitTypeMap.getValue()
					.entrySet()) {
				UnitAgent ua = entry.getValue();
				Unit myUnit = ua.unit;

				if (myUnit.getTypeID() == UnitTypes.Terran_Wraith.ordinal()) {
					wraithMicro(ua);
				} else if (myUnit.getTypeID() == UnitTypes.Terran_Marine
						.ordinal()) {
					Unit enemyUnit = game.getClosestEnemy(myUnit);

					// Get the unit to move if there's a target
					if (enemyUnit == null) {
						ua.task = UnitTask.IDLE;
						break;
					} else if (ua.task == UnitTask.IDLE) {
						ua.task = UnitTask.ATTACK_RUN;
					}

					// Calculate some values
					int maxCooldown = game.getWeaponType(
							game.getUnitType(myUnit.getTypeID())
									.getGroundWeaponID()).getDamageCooldown();
					int range = game.getWeaponType(
							game.getUnitType(myUnit.getTypeID())
									.getGroundWeaponID()).getMaxRange();
					int enemyRange = game.getWeaponType(
							game.getUnitType(enemyUnit.getTypeID())
									.getGroundWeaponID()).getMaxRange();

					// FSM
					if (ua.task == UnitTask.ATTACK_RUN) {
						// Move in on an attack run
						game.drawText(myUnit.getX(), myUnit.getY(),
								"Attack Run", false);
						game.drawLine(myUnit.getX(), myUnit.getY(),
								enemyUnit.getX(), enemyUnit.getY(),
								BWColor.YELLOW, false);
						game.move(myUnit.getID(), enemyUnit.getX(),
								enemyUnit.getY());
						// Fire when in range
						if (Point.distance(myUnit.getX(), myUnit.getY(),
								enemyUnit.getX(), enemyUnit.getY()) <= range) {
							ua.task = UnitTask.FIRING;
						}
					} else if (ua.task == UnitTask.FIRING) {
						// Attack
						game.drawText(myUnit.getX(), myUnit.getY(),
								"Attacking", false);
						game.drawLine(myUnit.getX(), myUnit.getY(),
								enemyUnit.getX(), enemyUnit.getY(),
								BWColor.RED, false);
						game.attack(myUnit.getID(), enemyUnit.getID());
						// Trigger animation lock
						ua.task = UnitTask.ANIMATION_LOCK;
						if (ua.unit.getTypeID() == UnitTypes.Terran_Marine
								.ordinal()) {
							ua.timeout = 5;
						} else {
							ua.timeout = 0;
						}
					} else if (ua.task == UnitTask.ANIMATION_LOCK) {
						// Can leave animation lock
						game.drawText(myUnit.getX(), myUnit.getY(),
								"Animation Lock (" + ua.timeout + ")", false);
						if (ua.timeout <= 0) {
							// Keep attacking
							if (range > enemyRange) {
								// Should kite
								ua.timeout = maxCooldown + 10;
								ua.task = UnitTask.RETREATING;
							} else {
								// Just fire all day!
								ua.task = UnitTask.FIRING;
							}
						}
					} else if (ua.task == UnitTask.RETREATING) {
						// Attack is on cooldown - retreat
						Point destPoint = retreat(myUnit.getX(), myUnit.getY(),
								64);
						game.drawText(myUnit.getX(), myUnit.getY(),
								"Retreating", false);
						game.drawLine(myUnit.getX(), myUnit.getY(),
								destPoint.x, destPoint.y, BWColor.GREEN, false);
						game.move(myUnit.getID(), destPoint.x, destPoint.y);
						// Switch to attack run when ready
						if (ua.timeout <= 0) {
							ua.task = UnitTask.ATTACK_RUN;
						}
					}

					// Reduce the timeout every frame
					ua.timeout -= 1;
				}
			}
		}
	}

	private void wraithMicro(UnitAgent ua) {
		Unit enemyUnit = game.getClosestEnemy(ua.unit);
		// Look for target
		if (enemyUnit == null) {
			// Scout
			setScoutingUnit(ua.unit);
			return;
		} else if (ua.task == UnitTask.IDLE) {
			ua.task = UnitTask.ATTACK_RUN;
		}

		// Calculate some values
		int maxCooldown = game.getWeaponType(
				game.getUnitType(ua.unit.getTypeID()).getGroundWeaponID())
				.getDamageCooldown();
		int range = game.getWeaponType(
				game.getUnitType(ua.unit.getTypeID()).getGroundWeaponID())
				.getMaxRange();
		int enemyRange = game.getWeaponType(
				game.getUnitType(enemyUnit.getTypeID()).getGroundWeaponID())
				.getMaxRange();

		// FSM
		if (ua.task == UnitTask.ATTACK_RUN) {
			// Move in on an attack run
			Point destPoint = attackRun(ua.unit.getX(), ua.unit.getY(), 64);
			game.drawText(ua.unit.getX(), ua.unit.getY(), "Attack Run", false);
			game.drawLine(ua.unit.getX(), ua.unit.getY(), destPoint.x,
					destPoint.y, BWColor.RED, false);
			game.move(ua.unit.getID(), destPoint.x, destPoint.y);
			// Fire when in range
			if (Point.distance(ua.unit.getX(), ua.unit.getY(),
					enemyUnit.getX(), enemyUnit.getY()) <= range) {
				ua.task = UnitTask.FIRING;
			}
		} else if (ua.task == UnitTask.FIRING) {
			// Attack
			game.drawText(ua.unit.getX(), ua.unit.getY(), "Attacking", false);
			game.drawLine(ua.unit.getX(), ua.unit.getY(), enemyUnit.getX(),
					enemyUnit.getY(), BWColor.RED, false);
			game.attack(ua.unit.getID(), enemyUnit.getID());
			// Trigger animation lock
			ua.task = UnitTask.ANIMATION_LOCK;
			if (ua.unit.getTypeID() == UnitTypes.Terran_Marine.ordinal()) {
				ua.timeout = 5;
			} else {
				ua.timeout = 0;
			}
		} else if (ua.task == UnitTask.ANIMATION_LOCK) {
			// Can leave animation lock
			game.drawText(ua.unit.getX(), ua.unit.getY(), "Animation Lock ("
					+ ua.timeout + ")", false);
			if (ua.timeout <= 0) {
				// Keep attacking
				if (range > enemyRange) {
					// Should kite
					ua.timeout = maxCooldown;
					ua.task = UnitTask.RETREATING;
				} else {
					// Just fire all day!
					ua.task = UnitTask.FIRING;
				}
			}
		} else if (ua.task == UnitTask.RETREATING) {
			// Attack is on cooldown - retreat
			Point destPoint = retreat(ua.unit.getX(), ua.unit.getY(), 64);
			game.drawText(ua.unit.getX(), ua.unit.getY(), "Retreating", false);
			game.drawLine(ua.unit.getX(), ua.unit.getY(), destPoint.x,
					destPoint.y, BWColor.GREEN, false);
			game.move(ua.unit.getID(), destPoint.x, destPoint.y);
			// Switch to attack run when ready
			if (ua.timeout <= 0) {
				ua.task = UnitTask.ATTACK_RUN;
			}
		}

		// Reduce the timeout every frame
		ua.timeout -= 1;
	}

	private Point attackRun(int x, int y, int distance) {
		// Grid coordinates of x and y
		int gx = (int) Math.round(x / 32);
		int gy = (int) Math.round(y / 32);
		if (distance <= 0 || gx <= 0 || gy <= 0 || gx >= mapWidth
				|| gy >= mapHeight) {
			return new Point(x, y);
		}

		Point bestAttack = new Point();

		double maxValue = Double.MIN_VALUE;
		double targetMapValue = targetMap[gx + 1][gy + 1];
		if (targetMapValue >= maxValue) {
			bestAttack.x = x + 32;
			bestAttack.y = y + 32;
			maxValue = targetMapValue;
		}
		targetMapValue = targetMap[gx + 1][gy - 1];
		if (targetMapValue >= maxValue) {
			bestAttack.x = x + 32;
			bestAttack.y = y - 32;
			maxValue = targetMapValue;
		}
		targetMapValue = targetMap[gx - 1][gy + 1];
		if (targetMapValue >= maxValue) {
			bestAttack.x = x - 32;
			bestAttack.y = y + 32;
			maxValue = targetMapValue;
		}
		targetMapValue = targetMap[gx - 1][gy - 1];
		if (targetMapValue >= maxValue) {
			bestAttack.x = x - 32;
			bestAttack.y = y - 32;
			maxValue = targetMapValue;
		}

		return retreat(bestAttack.x, bestAttack.y, distance - 32);
	}

	private Point retreat(int x, int y, int distance) {
		// Grid coordinates of x and y
		int gx = (int) Math.round(x / 32);
		int gy = (int) Math.round(y / 32);
		if (distance <= 0 || gx <= 0 || gy <= 0 || gx >= mapWidth
				|| gy >= mapHeight) {
			return new Point(x, y);
		}

		Point bestRetreat = new Point();

		double minValue = Double.MAX_VALUE;
		double threatMapValue = threatMap[gx + 1][gy + 1];
		if (threatMapValue <= minValue) {
			bestRetreat.x = x + 32;
			bestRetreat.y = y + 32;
			minValue = threatMapValue;
		}
		threatMapValue = threatMap[gx + 1][gy - 1];
		if (threatMapValue <= minValue) {
			bestRetreat.x = x + 32;
			bestRetreat.y = y - 32;
			minValue = threatMapValue;
		}
		threatMapValue = threatMap[gx - 1][gy + 1];
		if (threatMapValue <= minValue) {
			bestRetreat.x = x - 32;
			bestRetreat.y = y + 32;
			minValue = threatMapValue;
		}
		threatMapValue = threatMap[gx - 1][gy - 1];
		if (threatMapValue <= minValue) {
			bestRetreat.x = x - 32;
			bestRetreat.y = y - 32;
			minValue = threatMapValue;
		}

		return retreat(bestRetreat.x, bestRetreat.y, distance - 32);
	}

	private void scout() {
		// If I have no scouting unit assigned, don't scout
		if (scoutingUnit == null) {
			return;
		}

		// Acquire a target if necessary
		if (scoutingTarget == null) {
			scoutingTarget = getScoutingTarget();
		}

		// Issue commands as appropriate
		if (scoutingUnit != null && scoutingTarget != null) {
			game.move(scoutingUnit.getID(), scoutingTarget.getX(),
					scoutingTarget.getY());
		}
	}

	private Base getScoutingTarget() {
		Base target = null;
		for (Base b : baseManager) {
			if (b.getLocation().isStartLocation()) {
				if (target == null
						|| (b.getStatus() == BaseStatus.UNOCCUPIED && b
								.getLastScouted() < target.getLastScouted())) {
					target = b;
				}
			}
		}
		return target;
	}

	public void setScoutingUnit(Unit unit) {
		Worker w = baseManager.getWorker(scoutingUnit);
		if (w != null) {
			w.setBase(baseManager.main);
		}
		scoutingUnit = unit;
	}

	public boolean isScouting() {
		return scoutingUnit != null;
	}

	public void unitCreate(int unitID) {
		Unit unit = game.getUnit(unitID);
		UnitTypes type = UnitTypes.values()[unit.getTypeID()];
		if (type == UnitTypes.Terran_SCV) {
			return;
		}

		// Add a new hashmap if needed
		units.putIfAbsent(type, new HashMap<Integer, UnitAgent>());
		units.get(type).put(unitID, new UnitAgent(game, unit));
	}

	public void unitDestroy(Integer unitID) {
		Iterator<Entry<UnitTypes, HashMap<Integer, UnitAgent>>> i = units
				.entrySet().iterator();
		while (i.hasNext()) {
			i.next().getValue().remove(unitID);
		}

		if (unitID == scoutingUnit.getID()) {
			scoutingUnit = null;
		}
	}

	@Override
	public void registerDebugFunctions(GameHandler g) {
		// Threat map
		g.registerDebugFunction(new DebugModule() {
			@Override
			public void draw(DebugEngine engine) {
				// Actually draw
				for (int x = 1; x < mapWidth; x++) {
					for (int y = 1; y < mapHeight; y++) {
						game.drawCircle(x * 32, y * 32,
								(int) Math.round(threatMap[x][y]), BWColor.RED,
								false, false);
					}
				}
			}
		});
		// Weapon cooldown bars
		g.registerDebugFunction(new DebugModule() {
			@Override
			public void draw(DebugEngine engine) {
				for (Entry<UnitTypes, HashMap<Integer, UnitAgent>> unitTypeMap : units
						.entrySet()) {
					for (Entry<Integer, UnitAgent> entry : unitTypeMap
							.getValue().entrySet()) {
						UnitAgent ua = entry.getValue();
						Unit u = ua.unit;
						UnitType unitType = game.getUnitType(u.getTypeID());
						if (unitType.isCanAttackGround()
								|| unitType.isCanAttackAir()) {
							int cooldownBarSize = 20;
							int cooldownRemaining = u.getGroundWeaponCooldown();
							int maxCooldown = game.getWeaponType(
									unitType.getGroundWeaponID())
									.getDamageCooldown();
							game.drawLine(u.getX(), u.getY(), u.getX()
									+ cooldownBarSize, u.getY(), BWColor.GREEN,
									false);
							game.drawLine(u.getX(), u.getY(), u.getX()
									+ cooldownRemaining * cooldownBarSize
									/ maxCooldown, u.getY(), BWColor.RED, false);
						}
					}
				}
			}
		});
		// Scouting Target
		g.registerDebugFunction(new DebugModule() {
			@Override
			public void draw(DebugEngine engine) {
				if (scoutingTarget != null) {
					int x = scoutingTarget.getX();
					int y = scoutingTarget.getY();
					engine.drawLine(x - 10, y - 10, x + 10, y + 10,
							BWColor.RED, false);
					engine.drawLine(x + 10, y - 10, x - 10, y + 10,
							BWColor.RED, false);
				}
			}
		});
	}
}
