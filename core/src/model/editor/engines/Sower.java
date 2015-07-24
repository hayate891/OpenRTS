package model.editor.engines;

import geometry.geom2d.Point2D;
import geometry.geom3d.Point3D;
import geometry.math.AngleUtil;
import geometry.math.RandomUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import model.ModelManager;
import model.battlefield.map.Map;
import model.battlefield.map.Trinket;
import model.builders.MapArtisanUtil;
import model.builders.entity.TrinketBuilder;

public class Sower implements Runnable {
	private static final Logger logger = Logger.getLogger(Sower.class.getName());

	private static final int MAX_PLACES_COUNT = 30;
	private static final int MAX_TRINKETS_COUNT = 10;

	private volatile boolean pauseAsked = true;
	private volatile boolean paused = false;
	private List<Sowing> sowings = new ArrayList<>();

	private volatile Thread thread;

	public Sower() {
		Sowing treeOnCliff = new Sowing();
		treeOnCliff.addTrinket("Tree", 1, 1.5);
		treeOnCliff.addTrinket("Lun Tree", 1, 1.5);
		treeOnCliff.addTrinket("Plant", 1, 1);
		treeOnCliff.setCliffDist(4);
		treeOnCliff.setMaxSlope(10);
		treeOnCliff.addTexture("0", 0.5, 1);
		treeOnCliff.addTexture("11", 0, 0);
		sowings.add(treeOnCliff);

//		Sowing treeOnGrass = new Sowing();
//		treeOnGrass.addTrinket("Tree", 1, 3);
//		treeOnGrass.addTrinket("Lun Tree", 1, 3);
//		treeOnGrass.addTexture("1", 0.5, 1);
//		treeOnGrass.addTexture("11", 0, 0);
//		sowings.add(treeOnGrass);

		Sowing grass = new Sowing();
		grass.addTrinket("Tree", 1, 2);
		grass.addTrinket("LittleRock", 10, 0.5);
		grass.addTrinket("Herb2", 20, 0.8);
		grass.addTrinket("Herb", 20, 0.8);
		grass.addTexture("1", 0.5, 1);
		grass.addTexture("11", 0, 0);
		sowings.add(grass);

		Sowing rocks = new Sowing();
		rocks.addTrinket("LittleRock", 1, 1.5);
		rocks.addTexture("11", 0, 0);
		rocks.addTexture("3", 0.6, 1);
		sowings.add(rocks);

		Sowing rocksOnSlope = new Sowing();
		rocksOnSlope.addTrinket("LittleRock", 1, 0.3);
		rocksOnSlope.setMinSlope(20);
		rocksOnSlope.addTexture("11", 0, 0);
		rocksOnSlope.addTexture("3", 0.6, 1);
		sowings.add(rocksOnSlope);

		Sowing rocksAtCliffFoot = new Sowing();
		rocksAtCliffFoot.addTrinket("LittleRock", 1, 0.3);
		rocksAtCliffFoot.addTexture("11", 0, 0);
		rocksAtCliffFoot.addTexture("3", 0.6, 1);
		rocksAtCliffFoot.setCliffDist(3);
		sowings.add(rocksAtCliffFoot);
	}

	private void sowTrinket(Sowing s, Trinket t) {
		t.sowed = true;
		t.drawOnBattlefield();
		s.toGrow.add(t);
	}

	@Override
	public void run() {
		thread = Thread.currentThread();
		try {
			while (!Thread.currentThread().isInterrupted()) {
				if (pauseAsked) {
					synchronized (this) {
						paused = true;
						this.wait();
						paused = false;
						pauseAsked = false;
					}
				}

				for (Sowing s : sowings) {
					try{
						Trinket newTrinket;
						if (!s.toGrow.isEmpty() && RandomUtil.next() > 0.5) {
							newTrinket = grow(s);
						} else {
							newTrinket = findNewPlace(s);
						}
						if (newTrinket != null) {
							synchronized (ModelManager.getBattlefield().getMap()) {
								MapArtisanUtil.attachTrinket(newTrinket, ModelManager.getBattlefield().getMap());
							}
						}
					}catch(RuntimeException e){
						
					}
				}
//				Thread.sleep(50);
			}
		} catch (InterruptedException e) {
		}
	}

	private Trinket findNewPlace(Sowing s) {
		Map m = ModelManager.getBattlefield().getMap();
		Point2D randomPos = new Point2D(RandomUtil.next() * (m.xSize()-1),
				RandomUtil.next() * (m.ySize()-1));
		
		if (s.isAllowed(randomPos)) {
			int trinketIndex = RandomUtil.between(0, s.trinketBuilders.size());
			TrinketBuilder tb = s.trinketBuilders.get(trinketIndex);
			Trinket candidate = tb.build(randomPos.get3D(m.getAltitudeAt(randomPos)));
			candidate.separationRadius *= s.spacings.get(trinketIndex);
			boolean isValid = true;
			
			for (Trinket n : m.getInCircle(Trinket.class, randomPos, 10)) {
				double separationDistance = n.getSpacing(candidate);
				if (n.getDistance(candidate) < separationDistance) {
					isValid = false;
					break;
				}
			}
			if (isValid) {
				sowTrinket(s, candidate);
				return candidate;
			}
		}
		return null;
	}

	private Trinket grow(Sowing s) {
		Map m = ModelManager.getBattlefield().getMap();
		
		Trinket source = s.toGrow.get(RandomUtil.nextInt(s.toGrow.size()));
		List<Trinket> neibors = m.getInCircle(Trinket.class, source.getCoord(), 20);
		for (int i = 0; i < MAX_TRINKETS_COUNT; i++) {
			int trinketIndex = RandomUtil.between(0, s.trinketBuilders.size());
			Trinket candidate = s.trinketBuilders.get(trinketIndex).build(Point3D.ORIGIN);
			candidate.separationRadius *= s.spacings.get(trinketIndex);
			for (int j = 0; j < MAX_PLACES_COUNT; j++) {
				double separationDistance = source.getSpacing(candidate);
				Point2D place = source.getCoord().getTranslation(RandomUtil.between(0, AngleUtil.FULL),
						RandomUtil.between(separationDistance, separationDistance * 2));
				if (!m.isInBounds(place) || !s.isAllowed(place)) {
					continue;
				}

				boolean isValidePlace = true;
				for (Trinket n : neibors) {
					if (n.getCoord().getDistance(place) < n.getSpacing(candidate)) {
						isValidePlace = false;
						break;
					}
				}

				if (isValidePlace) {
					candidate.setPos(place.get3D(m.getAltitudeAt(place)));
					sowTrinket(s, candidate);
					return candidate;

				}
			}
		}
		s.toGrow.remove(source);
		return null;
	}

	public void askForPause() {
		pauseAsked = true;
	}

	public void unpause() {
		this.notify();
	}

	public boolean isPaused() {
		return paused;
	}

	public void destroy() {
		thread.interrupt();
	}

}
