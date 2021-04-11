package ru.rdude.rpg.game.logic.map;

import ru.rdude.rpg.game.logic.map.aStarImpl.MapRiverScorer;
import ru.rdude.rpg.game.logic.map.aStarImpl.MapRoadScorer;
import ru.rdude.rpg.game.logic.map.bioms.BiomCellProperty;
import ru.rdude.rpg.game.logic.map.bioms.Water;
import ru.rdude.rpg.game.logic.map.objects.City;
import ru.rdude.rpg.game.logic.map.objects.Dungeon;
import ru.rdude.rpg.game.logic.map.objects.MapObjectRoadAvailability;
import ru.rdude.rpg.game.logic.map.reliefs.ReliefCellProperty;
import ru.rdude.rpg.game.utils.Functions;
import ru.rdude.rpg.game.utils.TimeCounter;
import ru.rdude.rpg.game.utils.aStar.AStarGraph;
import ru.rdude.rpg.game.utils.aStar.AStarRouteFinder;
import ru.rdude.rpg.game.utils.aStar.AStarScorer;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.*;
import static ru.rdude.rpg.game.utils.Functions.*;

public class Generator {

    public enum WaterAlgorithm {AS_BIOM, SEPARATE_FROM_BIOM, MIXED, SMALL_ISLANDS, SUPER_MIXED, NO_WATER}

    private GameMap map;
    private int width; // 64
    private int height; // 64
    private List<BiomCellProperty> bioms; // available bioms
    private List<ReliefCellProperty> reliefs; // available reliefs

    private int citiesAmount; //  Need to be smaller on smaller maps
    private int dungeonsAmount;
    private List<Point> mapObjectsPoints;
    private List<City> cities;
    private List<Dungeon> dungeons;
    private Map<BiomCellProperty, Integer> biomAmount;
    // next parameters can be set directly with setter after creating Generator. Instead - default values:
    private double newBiomCoefficient; // chance to generate new biom instead of surrounding bioms
    private double newReliefCoefficient; // same with relief
    private boolean equalBioms; // generation type where biom trying to be equal size

    private WaterAlgorithm waterAlgorithm;
    private float waterAmount; // works only with separate water algorithm
    private int riversAmount;

    // zones help optimize finding unstepped cells on big maps
    private Map<CellProperty.Type, Set<Zone>> zones;

    public Generator(GameMapSize gameMapSize) {
        this(
                gameMapSize.getWidth(),
                gameMapSize.getHeight(),
                BiomCellProperty.getDefaultBiomes(),
                ReliefCellProperty.getDefaultReliefs(),
                gameMapSize.getWidth() / 32,
                gameMapSize.getWidth() / 4);
    }

    public Generator(int width, int height, List<BiomCellProperty> bioms, List<ReliefCellProperty> reliefs, int citiesAmount, int dungeonsAmount) {
        this.width = width;
        this.height = height;
        this.bioms = new ArrayList<>();
        this.bioms.addAll(bioms);
        this.reliefs = new ArrayList<>();
        this.reliefs.addAll(reliefs);
        this.citiesAmount = citiesAmount;
        this.dungeonsAmount = dungeonsAmount;
        zones = new HashMap<>();
        for (CellProperty.Type property : CellProperty.Type.values()) {
            zones.put(property, divideMapToZones());
        }
        biomAmount = new HashMap<>();
        fillBiomAmountMap(bioms);
        map = new GameMap(width, height);
        newBiomCoefficient = 0.004;
        newReliefCoefficient = 0.3;
        waterAlgorithm = WaterAlgorithm.MIXED;
        waterAmount = 0.33f;
        riversAmount = 10;

        equalBioms = true;

        mapObjectsPoints = new ArrayList<>();
        cities = new ArrayList<>();
        dungeons = new ArrayList<>();
    }

    public void setSize(GameMapSize size) {
        this.width = size.getWidth();
        this.height = size.getHeight();
    }

    public void setWaterAlgorithm(WaterAlgorithm waterAlgorithm) {
        this.waterAlgorithm = waterAlgorithm;
    }

    public void setNewBiomCoefficient(double newBiomCoefficient) {
        this.newBiomCoefficient = newBiomCoefficient;
    }

    public void setEqualBioms(boolean equalBioms) {
        this.equalBioms = equalBioms;
    }

    public GameMap createMap() {
        TimeCounter timeCounter = new TimeCounter("map generation");
        System.out.println(width + "x" + height + " (" + width * height + " cells)");

        switch (waterAlgorithm) {
            case SEPARATE_FROM_BIOM:
                createWater();
                bioms.remove(Water.getInstance());
                break;
            case NO_WATER:
                bioms.remove(Water.getInstance());
                break;
            case MIXED:
                createBioms();
                createWater();
                break;
            case SMALL_ISLANDS:
                createWaterWithSmallIslands();
                bioms.remove(Water.getInstance());
                break;
            case SUPER_MIXED:
                createWaterWithSmallIslands();
                createWater();
                break;

        }
        System.out.println(timeCounter.getCount("water creation"));


        createBioms();
        System.out.println(timeCounter.getCountFromPrevious("bioms creation"));
        createRivers();
        System.out.println(timeCounter.getCountFromPrevious("rivers creation"));
        denoiseBioms();
        System.out.println(timeCounter.getCountFromPrevious("bioms denoising"));
        createRelief();
        System.out.println(timeCounter.getCountFromPrevious("relief creation"));
        createCities();
        System.out.println(timeCounter.getCountFromPrevious("cities creation"));
        createDungeons();
        System.out.println(timeCounter.getCountFromPrevious("dungeons creation"));
        createRoads();
        System.out.println(timeCounter.getCountFromPrevious("roads creation"));
        createDeepOfWater();
        System.out.println(timeCounter.getCountFromPrevious("deep of water creation"));
        createLevels();
        System.out.println(timeCounter.getCountFromPrevious("leveling cells"));
        System.out.println(timeCounter.getCount());

        return map;
    }

    // Passing a list of bioms allows to use specific bioms on created map
    private void fillBiomAmountMap(List<BiomCellProperty> availableBioms) {
        availableBioms.forEach(biom -> biomAmount.put(biom, 0));
    }

    private List<Point> createStartPoints() {
        List<Point> points = new ArrayList<>();
        // point 1
        int y = (int) (height / 4 + floor(random(height / 5 * (-1), height / 5)));
        int x = (int) (width / 4 + floor(random(width / 5 * (-1), width / 5)));
        points.add(new Point(x, y));
        // point 2
        y = (int) (height - height / 4 + floor(random(height / 5 * (-1), height / 5)));
        x = (int) (width / 4 + floor(random(width / 5 * (-1), width / 5)));
        points.add(new Point(x, y));
        // point 3
        y = (int) (height / 4 + floor(random(height / 5 * (-1), height / 5)));
        x = (int) (width - width / 4 + floor(random(width / 5 * (-1), width / 5)));
        points.add(new Point(x, y));
        // point 4
        y = (int) (height - height / 4 + floor(random(height / 5 * (-1), height / 5)));
        x = (int) (width - width / 4 + floor(random(width / 5 * (-1), width / 5)));
        points.add(new Point(x, y));
        return points;
    }

    private Set<Zone> divideMapToZones() {
        Set<Zone> zones = new HashSet<>();
        int zonesAmountX = ((int) (Math.log(width) / Math.log(2)));
        int zonesAmountY = ((int) (Math.log(height) / Math.log(2)));
        int zoneWidth = width / zonesAmountX;
        int zoneHeight = height / zonesAmountY;

        if (width % zoneWidth != 0)
            zonesAmountX++;
        if (height % zoneHeight != 0)
            zonesAmountY++;

        for (int x = 0; x <= zonesAmountX; x++) {
            for (int y = 0; y <= zonesAmountY; y++) {
                int endX = x * zoneWidth + zoneWidth;
                if (endX > width - 1)
                    endX = width - 1;
                int endY = y * zoneHeight + zoneHeight;
                if (endY > height - 1)
                    endY = height - 1;
                zones.add(new Zone(x * zoneWidth, y * zoneHeight, endX, endY));
            }
        }

        return zones;
    }

    private BiomCellProperty randomBiom() {
        return bioms.get(random(0, bioms.size() - 1));
    }

    private ReliefCellProperty randomRelief() {
        return reliefs.get(random(0, reliefs.size() - 1));
    }


    private void increaseBiomAmount(BiomCellProperty biom) {
        biomAmount.put(biom, biomAmount.get(biom) + 1);
    }

    // looking for cells around, then everywhere
    private List<Cell> findUnSteppedCells(Cell cell, CellProperty.Type property) {
        List<Cell> result = new ArrayList<>();

        // close cells
        for (int i = 1; i <= 2; i++) {
            for (Cell aroundCell : cell.getAroundCells(i)) {
                if (cell.get(property) == null)
                    result.add(aroundCell);
            }
            if (!result.isEmpty()) return result;
        }

        // far cells from zones
        Zone zone = zones.get(property).stream()
                .filter(z -> z.hasCell(cell))
                .findFirst()
                .orElse(null);

        // if there is zone with unstepped cells and contains point
        if (zone != null) {
            for (int x = zone.getStartPoint().x; x <= zone.getEndPoint().x; x++) {
                for (int y = zone.getStartPoint().y; y <= zone.getEndPoint().y; y++) {
                    if (map.cell(x, y).get(property) == null) {
                        result.add(map.cell(x, y));
                        return result;
                    }
                }
            }
            zones.get(property).remove(zone);
        }

        // else check through other zones
        Set<Zone> zonesToRemove = new HashSet<>();
        zIter:
        for (Zone z : zones.get(property)) {
            for (int x = z.getStartPoint().x; x <= z.getEndPoint().x; x++) {
                for (int y = z.getStartPoint().y; y <= z.getEndPoint().y; y++) {
                    if (map.cell(x, y).get(property) == null) {
                        result.add(map.cell(x, y));
                        break zIter;
                    }
                }
            }
            zonesToRemove.add(z);
        }

        zones.get(property).removeAll(zonesToRemove);
        return result;
    }


    private boolean isChangeBiom(BiomCellProperty lastBiom, int cellsWithNoBiomAmount) {
        if (equalBioms && biomAmount.get(lastBiom) > cellsWithNoBiomAmount / bioms.size()) {
            if (lastBiom == Water.getInstance() && (waterAlgorithm == WaterAlgorithm.MIXED || waterAlgorithm == WaterAlgorithm.SUPER_MIXED))
                return random(1d) < newBiomCoefficient;
            else
                return true;
        }
        return random(1d) < newBiomCoefficient;
    }


    private void createBioms() {
        int nonNullCells = map.nonNullCells(CellProperty.Type.BIOM);
        int steps = height * width - nonNullCells;
        int cellsWithNoBiomAmount = steps;
        BiomCellProperty lastBiom;
        List<Point> points = createStartPoints();
        // generate random biom at start positions and 1 cell around:
        for (Point point : points) {
            BiomCellProperty randomBiom = randomBiom();
            if (map.cell(point).getBiom() == null) // cause water can be at this cell already
                steps -= 1;
            map.cell(point).setBiom(randomBiom);
            increaseBiomAmount(randomBiom);
            for (Cell aroundCell : map.cell(point).getAroundCells(1)) {
                increaseBiomAmount(randomBiom);
                if (aroundCell.getBiom() == null) // cause water can be at this cell already
                    steps -= 1;
                aroundCell.setBiom(randomBiom);
            }
        }
        // move through the map and generate bioms:
        while (steps > 0) {
            for (Point point : points) {
                lastBiom = map.cell(point).getBiom();
                // move to the next position:
                List<Cell> unSteppedCells = findUnSteppedCells(map.cell(point), CellProperty.Type.BIOM);
                if (unSteppedCells.isEmpty())
                    return;
                Cell unSteppedCell = random(unSteppedCells);
                point.x = unSteppedCell.getX();
                point.y = unSteppedCell.getY();
                // if absolutely new biom creating:
                if (isChangeBiom(lastBiom, cellsWithNoBiomAmount)) {
                    // next biom will be a biom with less present amount
                    BiomCellProperty biom = bioms.stream()
                            .min(Comparator.comparingInt(biom2 -> biomAmount.get(biom2)))
                            .orElse(lastBiom);
                    steps -= 1;
                    increaseBiomAmount(biom);
                    map.cell(point).setBiom(biom);
                    continue;
                }
                // else creating biom based on around cells:
                // creating biom coefficients from cells around:
                // close cells:
                Map<BiomCellProperty, Double> coefficients = map.cell(point).getAroundCells(1).stream()
                        .filter(cell -> cell.getBiom() != null)
                        .map(Cell::getBiom)
                        .collect(Collectors.toMap(BiomCellProperty::getThisInstance, v -> 7d, Double::sum));
                // far cells:
                coefficients.putAll(map.cell(point).getAroundCells(2).stream()
                        .filter(cell -> cell.getBiom() != null)
                        .map(Cell::getBiom)
                        .collect(Collectors.toMap(BiomCellProperty::getThisInstance, v -> 1d, Double::sum)));
                // extra coefficient to last biom if it presents around:
                if (coefficients.containsKey(lastBiom))
                    coefficients.put(lastBiom, coefficients.get(lastBiom) + 15);
                // get random biom based on coefficients:
                BiomCellProperty biom = randomWithWeights(coefficients);
                if (biom == null)
                    biom = randomBiom();
                steps -= 1;
                increaseBiomAmount(biom);
                map.cell(point).setBiom(biom);
            }
        }
    }

    private void createWater() {
        int steps = (int) (height * width * waterAmount);
        Water water = Water.getInstance();
        List<Point> points = createStartPoints();
        while (steps > 0) {
            for (Point point : points) {
                steps--;
                Point nextPoint = Functions.random(map.cell(point).getAroundCells(1)).point();
                point.x = nextPoint.x;
                point.y = nextPoint.y;

                map.cell(point).setBiom(Water.getInstance());
            }
        }
    }


    private void createWaterWithSmallIslands() {
        int steps = (int) (height * width * waterAmount);
        Water water = Water.getInstance();
        // creating start points for generation:
        List<Point> points = createStartPoints();
        // set start points to water:
        for (Point point : points) {
            map.cell(point).setBiom(water);
            steps -= 1;
        }
        while (steps > 0) {
            for (Point point : points) {
                // move to the next position:
                Point nextPoint = random(findUnSteppedCells(map.cell(point), CellProperty.Type.BIOM)).point();
                if (nextPoint == null) return;
                point.x = nextPoint.x;
                point.y = nextPoint.y;
                // generating:
                // not every cell visited by points will generate water
                steps -= 1;
                if (random(0, 1) > 0.75)
                    continue;
                // water biom will be set to the current point or to the random amount of around cells:
                // current point:
                if (random(0d, 1d) < 0.1) {
                    map.cell(point).setBiom(water);
                    continue;
                }
                // random around cells:
                List<Cell> cellsToAddWater = map.cell(point).getAroundCells(1);
                int amount = random(0, cellsToAddWater.size() - 1);
                while (amount > 0) {
                    Cell cell = cellsToAddWater.get(random(0, cellsToAddWater.size() - 1));
                    cellsToAddWater.remove(cell);
                    cell.setBiom(water);
                    amount--;
                }
            }
        }
    }

    private void createRivers() {
        Set<Cell> nodes = new HashSet<>();
        Map<Cell, Set<Cell>> connections = new HashMap<>();
        AStarScorer<Cell> scorer = new MapRiverScorer();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Cell cell = map.cell(x, y);
                nodes.add(cell);
                Set<Cell> aroundCells = new HashSet<>(cell.getAroundCells(1));
                connections.put(cell, aroundCells);
            }
        }

        AStarGraph<Cell> graph = new AStarGraph<>(nodes, connections);
        AStarRouteFinder<Cell> routeFinder = new AStarRouteFinder<>(graph, scorer, scorer);

        for (int i = 0; i < riversAmount; i++) {
            Cell from = map.cell(Functions.random(width - 1), Functions.random(height - 1));
            Cell to;
            if (!map.getCellsWithProperty(Water.getInstance()).isEmpty())
                to = Functions.random(map.getCellsWithProperty(Water.getInstance()));
            else
                to = map.cell(Functions.random(width - 1), Functions.random(height - 1));
            routeFinder.findRoute(from, to).forEach(cell -> cell.setBiom(Water.getInstance()));
        }
    }

    private void createDeepOfWater() {
        for (Cell cell : map.getCellsWithProperty(Water.getInstance())) {

            Water.DeepProperty deepProperty;

            if (cell.getAroundCells(1).stream()
                    .anyMatch(c -> c.getBiom() != Water.getInstance())) {
                deepProperty = Water.DeepProperty.SMALL;
            } else if (cell.getArea(4).stream()
                    .filter(c -> c.getBiom() != Water.getInstance())
                    .count() < 2) {
                deepProperty = Water.DeepProperty.DEEP;
            } else {
                deepProperty = Water.DeepProperty.NORMAL;
            }

            cell.setDeepProperty(deepProperty);
        }
    }

    private void createRelief() {
        int steps = height * width;
        List<Point> points = createStartPoints();
        ReliefCellProperty lastRelief;
        // generate random relief at start points:
        for (Point point : points) {
            ReliefCellProperty randomRelief = randomRelief();
            map.cell(point).setRelief(randomRelief);
            steps--;
            for (Cell aroundCell : map.cell(point).getAroundCells(1)) {
                aroundCell.setRelief(randomRelief);
                steps--;
            }
        }
        while (steps > 0) {
            for (Point point : points) {
                // moving through the map:
                lastRelief = map.cell(point).getRelief();
                Cell nextCell = random(findUnSteppedCells(map.cell(point), CellProperty.Type.RELIEF));
                point.x = nextCell.getX();
                point.y = nextCell.getY();
                // generating relief:
                // if random relief creating:
                if (random(1d) < newReliefCoefficient) {
                    map.cell(point).setRelief(random(reliefs));
                    steps--;
                    continue;
                }
                // else creating relief based on around cells:
                // creating relief coefficients from cells around:
                // close cells:
                Map<ReliefCellProperty, Double> coefficients = map.cell(point).getAroundCells(1).stream()
                        .filter(cell -> cell.getRelief() != null)
                        .map(Cell::getRelief)
                        .collect(Collectors.toMap(ReliefCellProperty::getThisInstance, v -> 7d, Double::sum));
                // far cells:
                coefficients.putAll(map.cell(point).getAroundCells(2).stream()
                        .filter(cell -> cell.getRelief() != null)
                        .map(Cell::getRelief)
                        .collect(Collectors.toMap(ReliefCellProperty::getThisInstance, v -> 1d, Double::sum)));
                // extra coefficient to last relief if it presents around:
                if (coefficients.containsKey(lastRelief))
                    coefficients.put(lastRelief, coefficients.get(lastRelief) + 15);
                // get random relief based on coefficients:
                ReliefCellProperty relief = randomWithWeights(coefficients);
                if (relief == null)
                    relief = randomRelief();
                steps -= 1;
                map.cell(point).setRelief(relief);
            }
        }
    }

    // reduce single biom cells
    private void denoiseBioms() {
        // denoising stops when next iteration do not denoise anything
        int denoisedAmount = 1;
        while (denoisedAmount > 0) {
            denoisedAmount = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int sameBiomAroundAmount = 0;
                    int waterAround = 0;
                    BiomCellProperty thisBiom = map.cell(x, y).getBiom();
                    // denoise only non water cells
                    if (thisBiom.equals(Water.getInstance()))
                        continue;
                    List<Cell> aroundCells = map.cell(x, y).getAroundCells(1);
                    for (Cell cell : aroundCells) {
                        if (thisBiom.equals(cell.getBiom()))
                            sameBiomAroundAmount++;
                        else if (cell.getBiom().equals(Water.getInstance()))
                            waterAround++;
                    }
                    // denoise if there are no same bioms around and this cell is not one-cell island
                    if (waterAround < 6 && sameBiomAroundAmount == 0) {
                        denoisedAmount++;
                        map.cell(x, y).setBiom(random(aroundCells).getBiom());
                    }
                }
            }
        }
    }


    private void createCities() {
        int currentID = mapObjectsPoints.size();
        List<Point> startingPoints = new ArrayList<>();

        // create starting points for cities
        while (startingPoints.size() < citiesAmount) {
            List<Point> newPoints = createStartPoints().stream()
                    .filter(newPoint -> {
                        Set<Point> existingPoints = new HashSet<>(startingPoints);
                        startingPoints.forEach(sp -> map.cell(sp).getArea(2).forEach(cell -> existingPoints.add(cell.point())));
                        return !existingPoints.contains(newPoint);
                    })
                    .collect(Collectors.toList());
            startingPoints.addAll(newPoints);
        }

        // create cities
        for (int i = 0; i < citiesAmount; i++) {
            Point point = startingPoints.get(i);
            while (map.cell(point).getBiom() == Water.getInstance()) {
                List<Cell> unsteppedCells = findUnSteppedCells(map.cell(point), CellProperty.Type.OBJECT);
                point = Functions.random(unsteppedCells).point();
            }
            City city = createCity(currentID);
            map.cell(point).setObject(city);
            currentID++;
            cities.add(city);
            mapObjectsPoints.add(point);
        }
    }

    private City createCity(int id) {
        return new City(id);
    }

    private void createDungeons() {
        int startID = mapObjectsPoints.size();
        for (int i = 0; i < dungeonsAmount; i++) {
            Dungeon currentDungeon = new Dungeon(startID + i);
            boolean notAllowedToPlace = true;
            int tries = 0;
            while (notAllowedToPlace) {
                if (tries > 100) {
                    break;
                }
                Cell cell = map.cell(random(width), random(height));
                if (cell.getObject() == null
                        && cell.getArea(2).stream().noneMatch(c -> c.getObject() != null)) {
                    notAllowedToPlace = false;
                    tries = 0;
                    cell.setObject(currentDungeon);
                    dungeons.add(currentDungeon);
                    mapObjectsPoints.add(cell.point());
                }
                tries++;
            }
        }
    }

    private void createRoads() {
        Set<Cell> nodes = new HashSet<>();
        Map<Cell, Set<Cell>> connections = new HashMap<>();
        AStarScorer<Cell> scorer = new MapRoadScorer();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Cell cell = map.cell(x, y);
                nodes.add(cell);
                Set<Cell> aroundCells = new HashSet<>(cell.getAroundCells(1));
                connections.put(cell, aroundCells);
            }
        }

        AStarGraph<Cell> graph = new AStarGraph<>(nodes, connections);
        AStarRouteFinder<Cell> routeFinder = new AStarRouteFinder<>(graph, scorer, scorer);

        List<Point> remainedObjects = mapObjectsPoints.stream()
                .filter(
                        point -> {
                            MapObjectRoadAvailability availability = map.cell(point).getObject().roadAvailability();
                            return map.cell(point).getBiom() != Water.getInstance()
                                    && (availability == MapObjectRoadAvailability.MUST
                                    || (availability == MapObjectRoadAvailability.CAN && randomBoolean()));
                        }
                ).collect(Collectors.toList());

        while (remainedObjects.size() > 2) {
            Point first = remainedObjects.get(remainedObjects.size() - 1);
            Point second = remainedObjects.get(random(remainedObjects.size() - 1));
            createRoad(routeFinder, map.cell(first), map.cell(second));
            remainedObjects.remove(first);
            remainedObjects.remove(second);
        }
    }

    private void createRoad(AStarRouteFinder<Cell> routeFinder, Cell from, Cell to) {
        List<Cell> route = routeFinder.findRoute(from, to);
        for (int i = 0; i < route.size() - 1; i++) {
            Cell cell = route.get(i);
            Road road = cell.getRoad() == null ? new Road() : cell.getRoad();
            if (cell.getObject() != null) {
                road.addDestination(cell.getObject().getPosition());
            }
            if (i > 0) {
                CellSide destination = cell.getRelativeLocation(route.get(i - 1));
                road.addDestination(destination);
            }
            if (i < route.size() - 1) {
                CellSide destination = cell.getRelativeLocation(route.get(i + 1));
                road.addDestination(destination);
            }
            cell.setRoad(road);
        }
    }

    private void createLevels() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int lvl;
                Cell cell = map.cell(x, y);
                if (cell.getObject() instanceof City || cell.getRoad() != null) {
                    lvl = Functions.random(1, 6);
                } else {
                    int howFarIsRoadOrCity = 5;
                    for (int i = 1; i < 5; i++) {
                        boolean hasRoadOrCity = cell.getAroundCells(i).stream()
                                .anyMatch(point -> cell.getRoad() != null || cell.getObject() instanceof City);
                        if (hasRoadOrCity) {
                            howFarIsRoadOrCity = i;
                            break;
                        }
                    }
                    lvl = Functions.random(10 * howFarIsRoadOrCity - 5, 10 * howFarIsRoadOrCity + 6);
                }
                cell.setLvl(lvl);
            }
        }
    }
}
