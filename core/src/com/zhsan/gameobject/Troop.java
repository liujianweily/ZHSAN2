package com.zhsan.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.zhsan.common.GlobalVariables;
import com.zhsan.common.Pair;
import com.zhsan.common.Point;
import com.zhsan.common.exception.FileReadException;
import com.zhsan.common.exception.FileWriteException;
import com.zhsan.gamecomponents.GlobalStrings;
import com.zhsan.lua.LuaAI;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Created by Peter on 8/8/2015.
 */
public class Troop implements HasPointBelongsFactionGameObject {

    public static final String SAVE_FILE = "Troop.csv";

    private enum OrderKind {
        IDLE, MOVE, MOVE_ENTER, ATTACK_LOCATION, ATTACK_TROOP, ATTACK_ARCH;

        static OrderKind fromCSV(String s) {
            switch (s) {
                case "idle": return IDLE;
                case "move": return MOVE;
                case "moveEnter": return MOVE_ENTER;
                case "attackLocation": return ATTACK_LOCATION;
                case "attackTroop": return ATTACK_TROOP;
                case "attackArch": return ATTACK_ARCH;
                default: assert false; return null;
            }
        }

        String toCSV() {
            switch (this) {
                case IDLE: return "idle";
                case MOVE: return "move";
                case MOVE_ENTER: return "moveEnter";
                case ATTACK_LOCATION: return "attackLocation";
                case ATTACK_TROOP: return "attackTroop";
                case ATTACK_ARCH: return "attackArch";
            }
            assert false;
            return null;
        }
    }

    private static class Order {
        public final OrderKind kind;
        public final Point targetLocation;
        public final int targetId;
        public final GameScenario scenario;

        private Order(GameScenario scenario, OrderKind kind, Point targetLocation) {
            this.scenario = scenario;
            this.kind = kind;
            this.targetLocation = targetLocation;
            this.targetId = -1;
        }

        private Order(GameScenario scenario, OrderKind kind, int targetId) {
            this.scenario = scenario;
            this.kind = kind;
            this.targetLocation = null;
            this.targetId = targetId;
        }

        static Order fromCSV(GameScenario scenario, String kind, String target) {
            OrderKind orderKind = OrderKind.fromCSV(kind);
            if (orderKind != null) {
                switch (orderKind) {
                    case IDLE:
                        return new Order(scenario, orderKind, null);
                    case MOVE:
                    case ATTACK_LOCATION:
                        return new Order(scenario, orderKind, Point.fromCSV(target));
                    case ATTACK_TROOP:
                    case ATTACK_ARCH:
                    case MOVE_ENTER:
                        return new Order(scenario, orderKind, Integer.parseInt(target));
                }
            }
            assert false;
            return null;
        }

        Pair<String, String> toCSV() {
            String orderKind = kind.toCSV();
            switch (kind) {
                case IDLE:
                    return new Pair<>(orderKind, "");
                case MOVE:
                case ATTACK_LOCATION:
                    return new Pair<>(orderKind, targetLocation.toCSV());
                case ATTACK_TROOP:
                case ATTACK_ARCH:
                case MOVE_ENTER:
                    return new Pair<>(orderKind, String.valueOf(targetId));
            }
            assert false;
            return null;
        }

        HasPointLocationGameObject target() {
            switch (kind) {
                case ATTACK_ARCH:
                    return scenario.getArchitectures().get(targetId);
                case ATTACK_TROOP:
                    return scenario.getTroops().get(targetId);
                case ATTACK_LOCATION:
                    return scenario.getTroopAt(targetLocation);
                default:
                    return null;
            }
        }
    }

    private GameScenario scenario;

    private Section belongedSection;

    private Point location;

    private Architecture startArchitecture;

    public static final Order ORDER_IDLE = new Order(null, OrderKind.IDLE, null);
    private Order order = ORDER_IDLE;

    private volatile boolean destroyed = false;

    private final int id;
    private String aiTags = "";

    @Override
    @LuaAI.ExportToLua
    public int getId() {
        return id;
    }

    @Override
    @LuaAI.ExportToLua
    public String getAiTags() {
        return aiTags;
    }

    @Override
    @LuaAI.ExportToLua
    public GameObject setAiTags(String aiTags) {
        this.aiTags = aiTags;
        return this;
    }

    public static final GameObjectList<Troop> fromCSV(FileHandle root, @NotNull GameScenario scen) {
        GameObjectList<Troop> result = new GameObjectList<>();

        FileHandle f = root.child(SAVE_FILE);
        try (CSVReader reader = new CSVReader(new InputStreamReader(f.read(), "UTF-8"))) {
            String[] line;
            int index = 0;
            while ((line = reader.readNext()) != null) {
                index++;
                if (index == 1) continue; // skip first line.

                Troop data = new Troop(Integer.parseInt(line[0]), scen);
                data.setAiTags(line[1]);
                data.location = Point.fromCSV(line[2]);
                data.order = Order.fromCSV(scen, line[3], line[4]);
                data.belongedSection = scen.getSections().get(Integer.parseInt(line[5]));
                data.startArchitecture = scen.getArchitectures().get(Integer.parseInt(line[6]));

                result.add(data);
            }
        } catch (IOException e) {
            throw new FileReadException(f.path(), e);
        }

        return result;
    }

    public static final void toCSV(FileHandle root, GameObjectList<Troop> types) {
        FileHandle f = root.child(SAVE_FILE);
        try (CSVWriter writer = new CSVWriter(f.writer(false, "UTF-8"))) {
            writer.writeNext(GlobalStrings.getString(GlobalStrings.Keys.TROOP_SAVE_HEADER).split(","));
            for (Troop detail : types) {
                Pair<String, String> orderStr = detail.order.toCSV();
                writer.writeNext(new String[]{
                        String.valueOf(detail.getId()),
                        detail.getAiTags(),
                        detail.location.toCSV(),
                        orderStr.x,
                        orderStr.y,
                        String.valueOf(detail.belongedSection.getId()),
                        String.valueOf(detail.startArchitecture.getId())
                });
            }
        } catch (IOException e) {
            throw new FileWriteException(f.path(), e);
        }
    }

    public Troop(int id, GameScenario scen) {
        this.id = id;
        this.scenario = scen;
    }

    @Override
    @LuaAI.ExportToLua
    public String getName() {
        return String.format(GlobalStrings.getString(GlobalStrings.Keys.TROOP_NAME), getLeaderName());
    }

    public Military getMilitary() {
        return Caches.get(Caches.troopMilitaries, this, () -> scenario.getMilitaries().filter(m -> m.getLocation() == this).getFirst());
    }

    public Point getPosition() {
        return location;
    }

    public Troop setLocation(Point location) {
        this.location = location;
        return this;
    }

    public String getOrderString() {
        switch (this.order.kind) {
            case IDLE:
                return null;
            case MOVE:
                return String.format(GlobalStrings.getString(GlobalStrings.Keys.MOVE_TO), this.order.targetLocation.x, this.order.targetLocation.y);
            case MOVE_ENTER:
                return String.format(GlobalStrings.getString(GlobalStrings.Keys.MOVE_TO_ENTER), scenario.getArchitectures().get(this.order.targetId));
            case ATTACK_LOCATION:
                return String.format(GlobalStrings.getString(GlobalStrings.Keys.ATTACK_POINT), this.order.targetLocation.x, this.order.targetLocation.y);
            case ATTACK_TROOP:
                return String.format(GlobalStrings.getString(GlobalStrings.Keys.ATTACK_TARGET), scenario.getTroops().get(this.order.targetId).getName());
            case ATTACK_ARCH:
                return String.format(GlobalStrings.getString(GlobalStrings.Keys.ATTACK_TARGET), scenario.getArchitectures().get(this.order.targetId).getName());
        }
        assert false;
        return null;
    }

    public String getKindString() {
        return this.getKind().getName();
    }

    public Section getBelongedSection() {
        return belongedSection;
    }

    public void setBelongedSection(Section s) {
        belongedSection = s;
    }

    @LuaAI.ExportToLua
    public Faction getBelongedFaction() {
        return getBelongedSection().getBelongedFaction();
    }

    @LuaAI.ExportToLua
    public MilitaryKind getKind() {
        if (scenario.getTerrainAt(getPosition()).isWater()) {
            return scenario.getDefaultShipKind();
        } else {
            return getMilitary().getKind();
        }
    }

    public Person getLeader() {
        return getMilitary().getLeader();
    }

    public String getLeaderName() {
        return getLeader().getName();
    }

    public int getMorale() {
        return getMilitary().getMorale();
    }

    public int getCombativity() {
        return getMilitary().getCombativity();
    }

    public Troop setStartArchitecture(Architecture startArchitecture) {
        this.startArchitecture = startArchitecture;
        return this;
    }

    @LuaAI.ExportToLua
    public int getCommand() {
        return (int) (getLeader().getCommand() +
                Math.max(getMilitary().getPersons().getAll().stream()
                                .max((p, q) -> p.getCommand() - q.getCommand())
                                .map(Person::getCommand)
                                .orElse(0) - getLeader().getCommand(), 0) * GlobalVariables.troopCommandPersonFactor);
    }

    @LuaAI.ExportToLua
    public int getStrength() {
        return (int) (getLeader().getStrength() +
                Math.max(getMilitary().getPersons().getAll().stream()
                        .max((p, q) -> p.getStrength() - q.getStrength())
                        .map(Person::getStrength)
                        .orElse(0) - getLeader().getStrength(), 0) * GlobalVariables.troopStrengthPersonFactor);
    }

    @LuaAI.ExportToLua
    public int getIntelligence() {
        return (int) (getLeader().getIntelligence() +
                Math.max(getMilitary().getPersons().getAll().stream()
                        .max((p, q) -> p.getIntelligence() - q.getIntelligence())
                        .map(Person::getIntelligence)
                        .orElse(0) - getLeader().getIntelligence(), 0) * GlobalVariables.troopIntelligencePersonFactor);
    }

    @LuaAI.ExportToLua
    public float getOffense() {
        return (getCommand() * 0.7f + getStrength() * 0.3f) / 100.0f *
                getMorale() / 100.0f *
                scenario.getMilitaryTerrain(getKind(), scenario.getTerrainAt(getPosition())).getMultiple() *
                (getKind().getOffense() + getKind().getOffensePerUnit() * getMilitary().getUnitCount());
    }

    @LuaAI.ExportToLua
    public float getDefense() {
        return getCommand() / 100.0f *
                getMorale() / 100.0f *
                scenario.getMilitaryTerrain(getKind(), scenario.getTerrainAt(getPosition())).getMultiple() *
                (getKind().getDefense() + getKind().getDefensePerUnit() * getMilitary().getUnitCount());
    }

    @LuaAI.ExportToLua
    public int getQuantity() {
        return getMilitary().getQuantity();
    }

    public float getUnitCount() {
        return getMilitary().getUnitCount();
    }

    public boolean loseQuantity(int quantity) {
        getMilitary().decreaseQuantity(quantity);
        boolean destroy = checkDestroy();
        if (destroy) {
            scenario.getTroops().getAll().stream()
                    .filter(x -> x.getTarget() == this)
                    .forEach(x -> x.order = ORDER_IDLE);
            this.getMilitary().getAllPersons().forEach(p -> p.moveToArchitecture(this.getPosition(), this.startArchitecture));
            destroy(true);
        }
        return destroy;
    }

    private boolean checkDestroy() {
        return this.getQuantity() <= 0;
    }

    private void destroy(boolean removeMilitary) {
        destroyed = true;
        scenario.removeTroop(this, removeMilitary);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public boolean canMoveInto(Point p) {
        Architecture destArch = scenario.getArchitectureAt(p);
        if (destArch != null && destArch.getBelongedFaction() != this.getBelongedFaction() && destArch.getEndurance() > 0) {
            return false;
        }
        Troop t = scenario.getTroopAt(p);
        if (t != null) {
            return false;
        }
        float val = scenario.getMilitaryTerrain(this.getKind(), scenario.getGameMap().getTerrainAt(p)).getAdaptability();
        if (val == Float.MAX_VALUE) {
            return false;
        }
        return true;
    }

    @LuaAI.ExportToLua
    public void giveMoveToEnterOrder(int archId) {
        giveMoveToEnterOrder(scenario.getArchitecture(archId));
    }

    @LuaAI.ExportToLua
    public void giveMoveToArchitectureOrder(int archId) {
        giveMoveToOrder(scenario.getArchitecture(archId).getPosition());
    }

    @LuaAI.ExportToLua
    public void giveAttackArchitectureOrder(int archId) {
        giveAttackOrder(scenario.getArchitecture(archId));
    }

    @LuaAI.ExportToLua
    public void giveAttackTroopOrder(int troopId) {
        giveAttackOrder(scenario.getTroops().get(troopId));
    }

    public void giveMoveToOrder(Point location) {
        this.order = new Order(scenario, OrderKind.MOVE, location);
    }

    public void giveMoveToEnterOrder(Architecture a) {
        this.order = new Order(scenario, OrderKind.MOVE_ENTER, a.getId());
    }

    public void giveAttackOrder(Point location) {
        this.order = new Order(scenario, OrderKind.ATTACK_LOCATION, location);
    }

    public void giveAttackOrder(Troop troop) {
        this.order = new Order(scenario, OrderKind.ATTACK_TROOP, troop.getId());
    }

    public void giveAttackOrder(Architecture architecture) {
        this.order = new Order(scenario, OrderKind.ATTACK_ARCH, architecture.getId());
    }

    @LuaAI.ExportToLua
    public GameObjectList<Troop> getFriendlyTroopsInView() {
        return scenario.getTroops().filter(t -> t.getPosition().taxiDistanceTo(this.getPosition()) <= 5 && t.getBelongedFaction() == this.getBelongedFaction());
    }

    @LuaAI.ExportToLua
    public GameObjectList<Troop> getHostileTroopsInView() {
        return scenario.getTroops().filter(t -> t.getPosition().taxiDistanceTo(this.getPosition()) <= 5 && t.getBelongedFaction() != this.getBelongedFaction());
    }

    @LuaAI.ExportToLua
    public boolean isArchitectureInView(int archId) {
        return this.getPosition().taxiDistanceTo(scenario.getArchitecture(archId).getPosition()) <= 5;
    }

    private Queue<Point> currentPath;
    private int currentMovability;

    private boolean attacked;

    public void initExecuteOrder() {
        Point targetLocation;
        if (this.order.targetLocation != null) {
            targetLocation = this.order.targetLocation;
        } else if (this.order.kind == OrderKind.ATTACK_ARCH || this.order.kind == OrderKind.MOVE_ENTER) {
            targetLocation = scenario.getArchitectures().get(this.order.targetId).getPosition();
        } else if (this.order.kind == OrderKind.ATTACK_TROOP) {
            targetLocation = scenario.getTroops().get(this.order.targetId).getPosition();
        } else {
            targetLocation = null;
        }

        if (targetLocation != null) {
            currentMovability = this.getMilitary().getKind().getMovability();
            currentPath = new ArrayDeque<>(scenario.getPathFinder(this).findPath(this.location, targetLocation));
            currentPath.poll();
        } else {
            currentPath = null;
        }

        attacked = false;
    }

    public boolean stepForward() {
        if (currentPath == null) {
            return true;
        }

        Point p = currentPath.poll();
        if (p == null) return false;

        if (!canMoveInto(p)) {
            return false;
        }

        float cost = scenario.getMilitaryTerrain(this.getKind(), scenario.getTerrainAt(p)).getAdaptability();

        if (cost <= currentMovability) {
            currentMovability -= cost;
            location = p;
        } else {
            return false;
        }

        return true;
    }

    public HasPointLocationGameObject getTarget() {
        return order.target();
    }

    public HasPointLocationGameObject canAttackTarget() {
        if (attacked) return null;

        HasPointLocationGameObject target = order.target();
        if ((target instanceof Architecture || target instanceof Troop) && isLocationInAttackRange(target.getPosition())) {
            return target;
        } else {
            return null;
        }
    }

    public boolean tryEnter(Point p) {
        if (order.kind == OrderKind.MOVE_ENTER) {
            Architecture a = scenario.getArchitectures().get(order.targetId);
            if (canEnter(p, a)) {
                enter(a);
                return true;
            }
        }
        return false;
    }

    public List<DamagePack> attack() {
        if (attacked) return Collections.emptyList();

        HasPointLocationGameObject target = order.target();
        if (target instanceof Architecture) {
            return attackArchitecture((Architecture) target);
        } else if (target instanceof Troop) {
            return attackTroop((Troop) target);
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isLocationInAttackRange(Point p) {
        int dist = getPosition().taxiDistanceTo(p);
        return getKind().getRangeLo() <= dist && dist <= getKind().getRangeHi();
    }

    private List<DamagePack> attackArchitecture(Architecture target) {
        Optional<Point> attackOptPoint = target.getLocations().stream().filter(this::isLocationInAttackRange).findFirst();
        if (!attackOptPoint.isPresent()) return Collections.emptyList();
        Point attackPoint = attackOptPoint.get();

        List<DamagePack> damagePacks = new ArrayList<>();

        float offense = this.getOffense();
        float defense = target.getDefense();
        float ratio = offense / defense;

        int damage = Math.round(GlobalVariables.baseArchitectureDamage * ratio * this.getKind().getArchitectureOffense());
        boolean destroy = target.loseEndurance(damage);
        damagePacks.add(new DamagePack(target, attackPoint, -damage, destroy));
        if (destroy) {
            scenario.getTroops().getAll().stream()
                    .filter(x -> x.getTarget() == target)
                    .forEach(x -> x.order = ORDER_IDLE);
        }

        int reactDamage;
        reactDamage = Math.round(GlobalVariables.baseDamage * (1 / ratio) * GlobalVariables.reactDamageFactor);
        destroy = this.loseQuantity(reactDamage);
        damagePacks.add(new DamagePack(this, this.getPosition(), -reactDamage, destroy));

        attacked = true;

        return damagePacks;
    }

    private List<DamagePack> attackTroop(Troop target) {
        if (!isLocationInAttackRange(target.getPosition())) return Collections.emptyList();

        List<DamagePack> damagePacks = new ArrayList<>();

        float offense = this.getOffense();
        float defense = target.getDefense();
        float ratio = offense / defense;

        int damage = Math.round(GlobalVariables.baseDamage * ratio);
        boolean destroy = target.loseQuantity(damage);
        damagePacks.add(new DamagePack(target, target.getPosition(), -damage, destroy));

        int reactDamage;
        if (target.isLocationInAttackRange(this.getPosition())) {
            reactDamage = Math.round(GlobalVariables.baseDamage * (1 / ratio) * GlobalVariables.reactDamageFactor);
            destroy = this.loseQuantity(reactDamage);
            damagePacks.add(new DamagePack(this, this.getPosition(), -reactDamage, destroy));
        }

        attacked = true;

        return damagePacks;
    }

    public boolean canEnter() {
        Iterator<Point> points = location.spiralOutIterator(1);
        while (points.hasNext()) {
            Point p = points.next();
            Architecture a = scenario.getArchitectureAt(p);
            if (a != null && a.getBelongedFaction() == this.getBelongedFaction()) {
                return true;
            }
        }
        return false;
    }

    public boolean canEnter(Point from, Architecture a) {
        Iterator<Point> points = from.spiralOutIterator(1);
        while (points.hasNext()) {
            Point p = points.next();
            Architecture arch = scenario.getArchitectureAt(p);
            if (a == arch) {
                return true;
            }
        }
        return false;
    }

    @LuaAI.ExportToLua
    public void enter() {
        Iterator<Point> points = location.spiralOutIterator(1);
        while (points.hasNext()) {
            Point p = points.next();
            Architecture a = scenario.getArchitectureAt(p);
            if (a != null && a.getBelongedFaction() == this.getBelongedFaction()) {
                enter(a);
                return;
            }
        }
    }

    public void enter(Architecture a) {
        this.getMilitary().setLocation(a);
        this.destroy(false);
    }

    @LuaAI.ExportToLua
    public boolean canOccupy() {
        Architecture a = scenario.getArchitectureAt(getPosition());
        return a != null && a.getBelongedFaction() != this.getBelongedFaction();
    }

    @LuaAI.ExportToLua
    public void occupy() {
        Architecture a = scenario.getArchitectureAt(getPosition());
        a.changeSection(this.getBelongedSection());
    }

}
